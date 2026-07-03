package services

import "errors"

var ErrNotFound = errors.New("not found")

var ErrKernelPrecondition = errors.New("kernel precondition failed")

var ErrKernelStateInvariant = errors.New("service state invariant violated")
