package rest

import (
	"log/slog"
	"net/http"
	"time"
)

// WithObservability logs each request and echoes the W3C traceparent header so
// the correlation id survives across service hops (worker -> customer).
func WithObservability(logger *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		traceParent := r.Header.Get("traceparent")
		if traceParent != "" {
			w.Header().Set("traceparent", traceParent)
		}

		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)

		logger.Info("request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", rec.status,
			"durationMs", time.Since(start).Milliseconds(),
			"traceparent", traceParent,
		)
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(code int) {
	s.status = code
	s.ResponseWriter.WriteHeader(code)
}
