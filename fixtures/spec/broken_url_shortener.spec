service BrokenUrlShortener {

  entity UrlMapping {
    click_count: Int
  }

  state {
    metadata: Int -> lone UrlMapping
  }

  operation Tamper {
    input: code: Int

    requires:
      code in metadata

    ensures:
      metadata'[code].click_count = pre(metadata)[code].click_count - 100
  }

  invariant clickCountNonNegative:
    all c in metadata | metadata[c].click_count >= 0
}
