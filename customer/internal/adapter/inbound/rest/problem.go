package rest

import (
	"encoding/json"
	"net/http"
)

const problemContentType = "application/problem+json"

// problem is an RFC 7807 problem detail document.
type problem struct {
	Type     string `json:"type"`
	Title    string `json:"title"`
	Status   int    `json:"status"`
	Detail   string `json:"detail,omitempty"`
	Instance string `json:"instance,omitempty"`
}

func writeProblem(w http.ResponseWriter, status int, typ, title, detail, instance string) {
	w.Header().Set("Content-Type", problemContentType)
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(problem{
		Type:     typ,
		Title:    title,
		Status:   status,
		Detail:   detail,
		Instance: instance,
	})
}
