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

	"github.com/grupomariposa/customer/internal/adapter/inbound/rest"
	"github.com/grupomariposa/customer/internal/adapter/outbound/memory"
	"github.com/grupomariposa/customer/internal/application"
	"github.com/grupomariposa/customer/internal/config"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	cfg := config.Load()

	// Composition root: outbound adapter -> use case -> inbound adapter.
	repo := memory.NewSeeded()
	svc := application.NewCustomerService(repo)
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
		logger.Info("customer listening", "addr", cfg.Addr)
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
