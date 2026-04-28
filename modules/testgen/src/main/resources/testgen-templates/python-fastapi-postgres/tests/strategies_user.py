"""User-supplied Hypothesis strategies referenced from spec convention rules.

Override an entity-typed strategy in the spec:

    conventions {
      LongURL.strategy = "tests.strategies_user:valid_url"
    }

Then define `valid_url` here as a zero-argument function that returns a Hypothesis
strategy. The generated `tests/strategies.py` will import it automatically.

This file is preserved across `spec-to-rest compile` runs — your edits are not
overwritten. Reset it by deleting it before re-running compile.
"""
from hypothesis import strategies as st  # noqa: F401

# Add your strategies below.
