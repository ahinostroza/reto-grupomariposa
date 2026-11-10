package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/grupomariposa/product-catalog/internal/adapter/inbound/rest"
	"github.com/grupomariposa/product-catalog/internal/adapter/outbound/memory"
	"github.com/grupomariposa/product-catalog/internal/application"
	"github.com/grupomariposa/product-catalog/internal/config"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := config.Load()

	// Dependency wiring (composition root): outbound adapter -> use case -> inbound adapter.
	repo := memory.NewSeeded()
	svc := application.NewProductService(repo)
	handler := rest.NewHandler(svc)
	router := rest.NewRouter(handler)

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           rest.WithObservability(logger, router),
		ReadHeaderTimeout: 5 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		logger.Info("product-catalog listening", "addr", cfg.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("server error", "err", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	logger.Info("shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "err", err)
	}
}
