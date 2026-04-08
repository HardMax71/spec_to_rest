service ConventionErrors {

  entity Widget {
    name: String
    weight: Float
  }

  enum Status {
    ACTIVE,
    INACTIVE,
  }

  state {
    widgets: Int -> one Widget
  }

  operation Create {
    input: name: String, weight: Float
    ensures:
      name' = name
  }

  operation GetWidget {
    input: id: Int
    output: name: String
  }

  conventions {
    Create.http_method = "POST"
    Create.http_path = "/widgets"
    Create.http_status_success = 201

    GetWidget.http_method = "GET"
    GetWidget.http_path = "/widgets/{id}"

    // Duplicate override
    Create.http_method = "PUT"

    // Unknown target
    Nonexistent.http_method = "GET"

    // Unknown property
    Create.http_frobnicate = "yes"

    // Invalid http_method value
    GetWidget.http_method = "GETPOST"

    // Invalid http_status_success (out of range)
    Create.http_status_success = 999

    // Entity property on operation target
    Create.db_table = "custom_table"

    // Operation property on entity target
    Widget.http_method = "GET"

    // Invalid http_path (no leading slash)
    GetWidget.http_path = "widgets"
  }
}
