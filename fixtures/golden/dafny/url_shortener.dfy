// AUTO-GENERATED Dafny skeleton for service UrlShortener.
// Spec-derived signatures and contracts are immutable; only method bodies are synthesized.

datatype Option<T> = None | Some(value: T)

ghost function TheBy<K, V>(m: map<K, V>, p: K -> bool): K
  requires exists k :: k in m && p(k)
  requires forall k1, k2 :: k1 in m && k2 in m && p(k1) && p(k2) ==> k1 == k2
  ensures TheBy(m, p) in m && p(TheBy(m, p))
{
  var k :| k in m && p(k); k
}

type ShortCode = string
predicate ShortCodeWhere(value: string)
{
  ((|value| >= 6 && |value| <= 10) && matches___a_zA_Z0_9___(value))
}
type LongURL = string
predicate LongURLWhere(value: string)
{
  (|value| > 0 && isValidURI(value))
}
type BaseURL = string
predicate BaseURLWhere(value: string)
{
  isValidURI(value)
}

datatype UrlMapping = UrlMapping(code: ShortCode, url: LongURL, created_at: int, click_count: int)

predicate UrlMappingInv(x: UrlMapping)
{
  (x.click_count >= 0)
  && (isValidURI(x.url))
}

class ServiceState
{
  var store: map<ShortCode, LongURL>
  var metadata: map<ShortCode, UrlMapping>
  var base_url: BaseURL
}

predicate ServiceStateInv(st: ServiceState)
  reads st
{
  (forall c :: (c in st.store) ==> (isValidURI(st.store[c])))
  && (st.store.Keys == st.metadata.Keys)
  && (forall c :: (c in st.metadata) ==> (st.metadata[c].click_count >= 0))
}

predicate isValidURI(x1: string)
{
  true
}
predicate matches___a_zA_Z0_9___(s: string)
{
  true
}

method Shorten(st: ServiceState, url: LongURL) returns (code: ShortCode, short_url: string)
  modifies st
  requires ServiceStateInv(st)
  requires LongURLWhere(url)
  requires isValidURI(url)
  ensures code !in old(st.store)
  ensures st.store == old(st.store)[code := url]
  ensures short_url == old(st.base_url) + "/" + code
  ensures |st.store| == |old(st.store)| + 1
  ensures st.metadata[code].url == url
  ensures st.metadata[code].click_count == 0
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Resolve(st: ServiceState, code: ShortCode) returns (url: LongURL)
  modifies st
  requires ServiceStateInv(st)
  requires ShortCodeWhere(code)
  requires code in st.store
  ensures url == old(st.store)[code]
  ensures st.store == old(st.store)
  ensures st.metadata[code].click_count == old(st.metadata)[code].click_count + 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method Delete(st: ServiceState, code: ShortCode)
  modifies st
  requires ServiceStateInv(st)
  requires ShortCodeWhere(code)
  requires code in st.store
  ensures code !in st.store
  ensures code !in st.metadata
  ensures |st.store| == |old(st.store)| - 1
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

method ListAll(st: ServiceState) returns (entries: set<UrlMapping>)
  modifies st
  requires ServiceStateInv(st)
  ensures entries == (set m | m in old(st.metadata) && true :: old(st.metadata)[m])
  ensures st.store == old(st.store)
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}
