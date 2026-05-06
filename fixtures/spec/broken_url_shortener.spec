service BrokenUrlShortener {

  entity UrlMapping {
    click_count: Int
  }

  state {
    metadata: Int -> lone UrlMapping
    totalClicks: Int
  }

  operation Tamper {
    input: code: Int

    requires:
      code in metadata

    ensures:
      metadata'[code].click_count = pre(metadata)[code].click_count - 100
  }

  operation Drain {
    requires:
      true

    ensures:
      totalClicks' = -1
  }

  invariant clickCountNonNegative:
    all c in metadata | metadata[c].click_count >= 0

  invariant totalClicksNonNegative:
    totalClicks >= 0
}
