//go:build !conformance

package testadmin

import (
	"github.com/go-chi/chi/v5"
	"github.com/uptrace/bun"
)

// Register is a no-op in normal builds; the spec-derived conformance
// implementation (build tag `conformance`, emitted by default — opt out
// with --no-tests) replaces it via the build constraint.
func Register(_ chi.Router, _ *bun.DB) {}
