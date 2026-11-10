package config

import "os"

// Config holds runtime configuration sourced from the environment, with safe
// defaults for local development.
type Config struct {
	Addr string
}

func Load() Config {
	return Config{
		Addr: getenv("HTTP_ADDR", ":8081"),
	}
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
