service UrlShortener {

  // --- Type Definitions ---

  type ShortCode = String where len(value) >= 6 and len(value) <= 10
                              and value matches /^[a-zA-Z0-9]+$/

  type LongURL = String where len(value) > 0 and isValidURI(value)

  type BaseURL = String where isValidURI(value)

  // --- Entities ---

  entity UrlMapping {
    code: ShortCode
    url: LongURL
    created_at: DateTime
    click_count: Int where value >= 0

    invariant: isValidURI(url)
  }

  // --- State ---

  state {
    store: ShortCode -> lone LongURL
    metadata: ShortCode -> lone UrlMapping
    base_url: BaseURL
  }

  // --- Operations ---

  operation Shorten {
    input:  url: LongURL
    output: code: ShortCode, short_url: String

    requires:
      isValidURI(url)

    ensures:
      code not in pre(store)
      store' = pre(store) + {code -> url}
      short_url = base_url + "/" + code
      #store' = #pre(store) + 1
      metadata'[code].url = url
      metadata'[code].click_count = 0
  }

  operation Resolve {
    input:  code: ShortCode
    output: url: LongURL

    requires:
      code in store

    ensures:
      url = store[code]
      store' = store
      metadata'[code].click_count = pre(metadata)[code].click_count + 1
  }

  operation Delete {
    input: code: ShortCode

    requires:
      code in store

    ensures:
      code not in store'
      code not in metadata'
      #store' = #pre(store) - 1
  }

  operation ListAll {
    output: entries: Set[UrlMapping]

    requires:
      true

    ensures:
      entries = { m in metadata | true }
      store' = store
  }

  // --- Global Invariants ---

  invariant allURLsValid:
    all c in store | isValidURI(store[c])

  invariant metadataConsistent:
    dom(store) = dom(metadata)

  invariant clickCountNonNegative:
    all c in metadata | metadata[c].click_count >= 0

  // --- Convention Overrides ---

  conventions {
    Shorten.http_method = "POST"
    Shorten.http_path = "/shorten"
    Shorten.http_status_success = 201

    Resolve.http_method = "GET"
    Resolve.http_path = "/{code}"
    Resolve.http_status_success = 302
    Resolve.http_header "Location" = output.url

    Delete.http_method = "DELETE"
    Delete.http_path = "/{code}"
    Delete.http_status_success = 204

    ListAll.http_method = "GET"
    ListAll.http_path = "/urls"
    ListAll.http_status_success = 200
  }
}
