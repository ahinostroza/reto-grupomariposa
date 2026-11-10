# Order Enrichment Platform

Worker de pedidos para Grupo Mariposa. Consume pedidos desde Kafka, los enriquece
contra dos APIs de dominio escritas en Go, valida las reglas de negocio y guarda el
resultado en MongoDB. Está pensado para no perder ni duplicar pedidos: reintentos
con backoff, idempotencia, dead-letter topic, lock distribuido y métricas.

El sistema son tres servicios que se despliegan por separado:

| Servicio          | Stack                         | Puerto | Qué hace                                                  |
|-------------------|-------------------------------|:------:|-----------------------------------------------------------|
| `order-worker`    | Java 21, Spring Boot, WebFlux | 8083   | Consume de Kafka, enriquece, valida y persiste            |
| `product-catalog` | Go                            | 8081   | Catálogo de productos (consulta individual y en lote)     |
| `customer`        | Go                            | 8082   | Clientes y su estado (ACTIVE / BLOCKED / INACTIVE)        |

Cada servicio es dueño de sus datos; no comparten base de datos ni código de
dominio. Lo único que comparten es el contrato OpenAPI de cada API Go
(`<servicio>/api/openapi.yaml`). El worker genera sus DTOs cliente a partir de esos
contratos en tiempo de compilación (`openapi-generator`), así que si una API Go
cambia de forma incompatible, el worker deja de compilar en vez de fallar en
producción.

## Arquitectura

```
          Kafka (topic: orders)   entrega at-least-once, key = orderId
                   │
                   ▼
        ┌────────────────────────┐
        │      order-worker      │  Java 21 · Spring Boot · WebFlux
        │  reactivo de punta a    │  (reactor-kafka, Mongo reactivo, WebClient)
        │       punta             │
        └───┬──────────┬──────┬──┘
            │          │      │           envía fallidos ─► orders.DLT
      WebClient    Redisson   Mongo
            │       (lock +   reactivo
            │        fallidos)
   ┌────────┴────┐  ┌──┴────┐  ┌┴─────────────┐
   │ APIs Go      │  │ Redis │  │   MongoDB    │
   │ catalog +    │  │       │  │ processed_   │
   │ customer     │  │       │  │   orders     │
   └──────────────┘  └───────┘  └──────────────┘
```

El flujo de cada mensaje, en orden:

1. Toma un lock distribuido por `orderId` (evita que dos instancias procesen el
   mismo pedido a la vez).
2. Revisa si el pedido ya fue procesado; si lo fue, lo saltea (idempotencia).
3. Enriquece: pide productos y cliente **en paralelo** (`Mono.zip`).
4. Valida: que existan todos los productos y que el cliente esté `ACTIVE`.
5. Persiste con `upsert` por `orderId` y recién ahí confirma el offset en Kafka.

Si algo falla de forma transitoria (p. ej. contención de lock) se reintenta con
backoff. Si es un error de negocio (producto inexistente, cliente bloqueado,
mensaje inválido) o se agotaron los reintentos, el pedido va al topic `orders.DLT`
y queda registrado en Redis con un contador de intentos. El offset se confirma solo
después de persistir o de mandar al DLT, así un mensaje no se pierde ni bloquea la
partición.

## Requisitos

Para levantar todo solo necesitás **Docker** y **Docker Compose v2**. No hace falta
tener Java ni Go instalados: cada servicio se construye dentro de su imagen.

Si querés correr las pruebas o compilar fuera de Docker: JDK 21, Go 1.27 y Maven
(o usá el wrapper `./mvnw` que viene en `order-worker`).

## Levantar el entorno

```bash
docker compose up --build
```

Esto construye las tres imágenes y arranca Kafka, MongoDB y Redis. El worker espera
a que las dependencias estén sanas (`depends_on` + healthchecks) antes de empezar a
consumir. La primera vez tarda un poco porque descarga dependencias de Maven y las
imágenes base.

Cuando termina, deberías ver los seis contenedores arriba:

```bash
docker compose ps
```

Las APIs Go traen datos de ejemplo cargados en memoria para poder probar sin seedear
nada:

- Productos: `product-789` (Laptop, activo), `product-123`, `product-456`,
  `product-999` (inactivo).
- Clientes: `customer-456` (ACTIVE), `customer-111` (BLOCKED), `customer-222`
  (INACTIVE).

Cada API Go además publica su documentación interactiva, y el worker expone sus
endpoints de operación:

- Catálogo (Swagger UI) → http://localhost:8081/docs
- Clientes (Swagger UI) → http://localhost:8082/docs
- Worker · salud → http://localhost:8083/actuator/health
- Worker · métricas (Prometheus) → http://localhost:8083/actuator/prometheus

### Si los puertos están ocupados

Los puertos del host (8081, 8082, 8083, 27017, 6379, 9094) son configurables por
variables de entorno; los puertos internos entre contenedores no cambian. Si, por
ejemplo, ya tenés algo en 8081/8082, copiá `.env.example` a `.env` y ajustá:

```bash
cp .env.example .env
# editá CATALOG_PORT, CUSTOMER_PORT, etc.
```

o pasá las variables directamente al comando:

```bash
CATALOG_PORT=18081 CUSTOMER_PORT=18082 WORKER_PORT=18083 \
MONGO_PORT=27018 REDIS_PORT=6380 docker compose up --build
```

## Probarlo de punta a punta

### Caso feliz: un pedido que se procesa

Publicá un pedido en el topic `orders`. La key del mensaje es el `orderId` para que
los pedidos de un mismo id mantengan su orden dentro de la partición. Como el valor
es JSON (que lleva `:`), uso `~` como separador de key:

```bash
docker compose exec kafka kafka-console-producer \
  --bootstrap-server localhost:9092 --topic orders \
  --property parse.key=true --property key.separator='~'
```

Y pegá esta línea:

```
order-123~{"orderId":"order-123","customerId":"customer-456","productIds":["product-789"]}
```

El producer queda esperando más líneas; salí con `Ctrl+D` (o `Ctrl+C`) cuando
termines. El topic `orders` se crea solo al publicar el primer mensaje.

En los logs del worker aparece:

```bash
docker compose logs order-worker | grep order-123
# ... OrderConsumer : order order-123 processed: PROCESSED
```

Y el pedido queda en MongoDB con la estructura pedida:

```bash
docker compose exec mongo mongosh orders --quiet \
  --eval 'db.processed_orders.find({orderId:"order-123"}, {_id:0}).pretty()'
```

```json
{
  "orderId": "order-123",
  "customerId": "customer-456",
  "products": [ { "productId": "product-789", "name": "Laptop", "price": 99900 } ]
}
```

El precio se guarda como entero en unidades menores (99900 = 999.00), nunca como
decimal; `mongosh` lo muestra como `Long("99900")`, que es ese mismo entero.

### Caso de error: un pedido que va al dead-letter

Mandá un pedido con un producto que no existe:

```
order-404~{"orderId":"order-404","customerId":"customer-456","productIds":["ghost"]}
```

No se persiste. En su lugar:

```bash
# el worker lo loguea como dead-letter
docker compose logs order-worker | grep order-404
# ... dead-lettering order order-404: ProductNotFoundException: Products not found in catalog: [ghost]

# queda el contador de intentos en Redis
docker compose exec redis redis-cli get "failed:order:order-404:attempts"   # -> 1

# y no está en Mongo
docker compose exec mongo mongosh orders --quiet \
  --eval 'db.processed_orders.countDocuments({orderId:"order-404"})'        # -> 0
```

Para apagar todo: `docker compose down`.

## Correr las pruebas

El worker separa pruebas unitarias (rápidas, sin infraestructura) de las de
integración (levantan Kafka, MongoDB y Redis reales con Testcontainers, y usan un
mock HTTP para las APIs Go):

```bash
cd order-worker
./mvnw test      # solo unitarias
./mvnw verify    # unitarias + integración (requiere Docker)
```

Las de integración se saltan solas si no hay un Docker disponible, así que el build
no se rompe en un entorno sin Docker.

Los servicios Go:

```bash
cd product-catalog && go test ./...
cd customer        && go test ./...
```

## Observabilidad

El worker expone Actuator. Las métricas salen en formato Prometheus en
`http://localhost:8083/actuator/prometheus`, y hay health en `/actuator/health`.
Además de las métricas estándar de la JVM, agregué
contadores de negocio: `orders.processed`, `orders.skipped_duplicate`,
`orders.dead_lettered` y el timer `orders.processing`.

Para trazabilidad uso Micrometer Tracing: cada log del worker lleva su
`traceId`/`spanId`, y el `traceparent` (W3C) viaja en las llamadas WebClient hacia
las APIs Go, que lo reenvían. Así se puede seguir un pedido de punta a punta.

## Decisiones de diseño

Algunas cosas que decidí a propósito y por qué:

- **WebFlux, aunque para esto virtual threads sería más simple.** El enunciado pide
  WebFlux, así que el worker es reactivo de punta a punta. Dicho eso, para un worker
  de Kafka como este, virtual threads (Java 21) con código imperativo daría un
  resultado igual de escalable y más fácil de leer. Fui por WebFlux porque es un
  requisito, no porque sea la única buena opción.
- **At-least-once + idempotencia, no exactly-once.** El offset se confirma recién
  después de guardar (o de mandar al DLT). Un exactly-once real a través de
  Kafka → Go → Mongo pediría transacciones distribuidas o un outbox; con
  at-least-once y `upsert` por `orderId` consigo el mismo resultado observable con
  mucho menos costo.
- **Reintentos en un solo lugar.** Las fallas HTTP transitorias las reintenta el
  adaptador (con circuit breaker por servicio, para que una caída del catálogo no
  afecte al de clientes). El consumidor solo reintenta lo que el adaptador no cubre,
  como la contención de lock. Los errores de negocio (un 404, un mensaje mal
  formado) no se reintentan nunca. El máximo de reintentos, el tiempo de espera
  (backoff) y los umbrales del circuit breaker son configurables desde
  `application.yml` o variables de entorno.
- **El lock es por `orderId`.** El enunciado mezcla "clientes bloqueados" con "el
  mismo pedido"; el requisito de correctitud es no procesar dos veces el mismo
  pedido, así que bloqueo por `orderId`. Serializar por cliente es otra cosa, más
  cara, y se puede agregar si de verdad hiciera falta.
- **Mensajes en JSON, sin Schema Registry.** El enunciado define los mensajes en
  JSON. Un registry con Avro/Protobuf aporta evolución de esquemas, pero ese
  beneficio no lo ejercita un reto puntual y sumaría otro servicio; es el paso
  natural si el contrato de mensajes empezara a cambiar seguido en producción.
- **Dinero como entero en unidades menores y estado del cliente como enum**, para no
  arrastrar errores de punto flotante ni quedar atados a un booleano cuando aparezca
  un tercer estado.

## Estructura del repositorio

```
.
├── order-worker/            # worker Java (arquitectura hexagonal)
│   ├── src/main/java/.../domain          # modelo y reglas, sin framework
│   ├── src/main/java/.../application     # casos de uso + puertos
│   └── src/main/java/.../infrastructure  # adapters: kafka, mongo, redis, http
├── product-catalog/         # API Go de catálogo (+ api/openapi.yaml, Dockerfile)
├── customer/                # API Go de clientes (+ api/openapi.yaml, Dockerfile)
├── docker-compose.yml       # Kafka + MongoDB + Redis + los tres servicios
└── .env.example             # puertos y config configurables
```
