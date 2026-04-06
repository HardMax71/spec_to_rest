import { describe, it, expect } from "vitest";
import { parseSpec } from "#parser/index.js";

function expectParses(input: string) {
  const { errors } = parseSpec(input);
  if (errors.length > 0) {
    throw new Error(
      `Parse errors:\n${errors.map((e) => `  ${e.line}:${e.column} ${e.message}`).join("\n")}`,
    );
  }
}

describe("Entity declarations", () => {
  it("parses entity with fields and where constraints", () => {
    expectParses(`
      service T {
        entity User {
          id: Int where value > 0
          name: String where len(value) >= 1 and len(value) <= 100
          email: String
        }
      }
    `);
  });

  it("parses entity with invariants", () => {
    expectParses(`
      service T {
        entity Todo {
          status: String
          completed_at: Option[DateTime]
          invariant: status = "done" implies completed_at != none
        }
      }
    `);
  });

  it("parses entity with extends", () => {
    expectParses(`
      service T {
        entity Base { id: Int }
        entity Child extends Base { name: String }
      }
    `);
  });
});

describe("Enum declarations", () => {
  it("parses basic enum", () => {
    expectParses(`
      service T {
        enum Status { TODO, IN_PROGRESS, DONE }
      }
    `);
  });

  it("parses enum with trailing comma", () => {
    expectParses(`
      service T {
        enum Color { RED, GREEN, BLUE, }
      }
    `);
  });
});

describe("Type aliases", () => {
  it("parses simple type alias with where clause", () => {
    expectParses(`
      service T {
        type Money = Int where value >= 0
      }
    `);
  });

  it("parses type alias with regex constraint", () => {
    expectParses(`
      service T {
        type Email = String where value matches /^[^@]+@[^@]+$/
      }
    `);
  });
});

describe("State declarations", () => {
  it("parses state with relation types", () => {
    expectParses(`
      service T {
        state {
          items: Int -> lone String
          required: Int -> one String
          multiple: Int -> some String
          any_count: Int -> set String
        }
      }
    `);
  });

  it("parses state with plain types", () => {
    expectParses(`
      service T {
        state {
          count: Int
          name: String
          tags: Set[String]
          mapping: Map[String, Int]
          history: Seq[String]
          maybe: Option[Int]
        }
      }
    `);
  });
});

describe("Operation declarations", () => {
  it("parses operation with input, output, requires, ensures", () => {
    expectParses(`
      service T {
        state { items: Int -> lone String }
        operation Add {
          input: key: Int, val: String
          output: result: String
          requires:
            key > 0
            key not in items
          ensures:
            items' = pre(items) + {key -> val}
            result = val
        }
      }
    `);
  });

  it("parses operation without input", () => {
    expectParses(`
      service T {
        state { count: Int }
        operation GetCount {
          output: n: Int
          requires: true
          ensures: n = count
        }
      }
    `);
  });

  it("parses operation without output", () => {
    expectParses(`
      service T {
        state { count: Int }
        operation Reset {
          input: val: Int
          requires: val >= 0
          ensures: count' = val
        }
      }
    `);
  });
});

describe("Transition declarations", () => {
  it("parses state machine transitions", () => {
    expectParses(`
      service T {
        enum Status { OPEN, CLOSED }
        entity Item { status: Status }
        transition ItemLifecycle {
          entity: Item
          field: status
          OPEN -> CLOSED via CloseItem
          CLOSED -> OPEN via ReopenItem when updated_at > closed_at
        }
      }
    `);
  });
});

describe("Invariant and fact declarations", () => {
  it("parses named invariant", () => {
    expectParses(`
      service T {
        state { count: Int }
        invariant positive: count >= 0
      }
    `);
  });

  it("parses anonymous invariant", () => {
    expectParses(`
      service T {
        state { count: Int }
        invariant: count >= 0
      }
    `);
  });

  it("parses fact declaration", () => {
    expectParses(`
      service T {
        state { items: Int -> lone String }
        fact itemsBounded: #items <= 1000
      }
    `);
  });
});

describe("Function and predicate declarations", () => {
  it("parses function", () => {
    expectParses(`
      service T {
        function add(a: Int, b: Int): Int = a + b
      }
    `);
  });

  it("parses predicate", () => {
    expectParses(`
      service T {
        predicate isPositive(n: Int) = n > 0
      }
    `);
  });
});

describe("Convention block", () => {
  it("parses simple convention properties", () => {
    expectParses(`
      service T {
        state { count: Int }
        operation Get {
          output: n: Int
          requires: true
          ensures: n = count
        }
        conventions {
          Get.http_method = "GET"
          Get.http_path = "/count"
          Get.http_status_success = 200
        }
      }
    `);
  });

  it("parses parameterized convention property", () => {
    expectParses(`
      service T {
        state { url: String }
        operation Resolve {
          output: target: String
          requires: true
          ensures: target = url
        }
        conventions {
          Resolve.http_header "Location" = output.url
        }
      }
    `);
  });
});

describe("Import declarations", () => {
  it("parses import statement", () => {
    expectParses(`
      import "other_service.spec"
      service T {}
    `);
  });

  it("parses multiple imports", () => {
    expectParses(`
      import "a.spec"
      import "b.spec"
      service T {}
    `);
  });
});

describe("Empty service", () => {
  it("parses service with no members", () => {
    expectParses(`service Empty {}`);
  });
});
