# Multi-stage build: compile a static binary, ship it on a distroless base.
FROM golang:1.27 AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -o /out/server ./cmd/server

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/server /server
EXPOSE 8081
ENV HTTP_ADDR=:8081
ENTRYPOINT ["/server"]
