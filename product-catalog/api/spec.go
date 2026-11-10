package api

import _ "embed"

// Spec is the OpenAPI contract, embedded into the binary so it can be served at
// runtime (and shipped in the distroless image) without a filesystem dependency.
//
//go:embed openapi.yaml
var Spec []byte
