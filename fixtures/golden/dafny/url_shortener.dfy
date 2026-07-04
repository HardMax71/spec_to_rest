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
  (|value| >= 6 && matches___a_zA_Z0_9___(value))
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
  && (ShortCodeWhere(x.code))
  && (LongURLWhere(x.url))
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
  && (forall k :: k in st.store ==> ShortCodeWhere(k))
  && (forall k :: k in st.store ==> LongURLWhere(st.store[k]))
  && (forall k :: k in st.metadata ==> ShortCodeWhere(k))
  && (forall k :: k in st.metadata ==> UrlMappingInv(st.metadata[k]))
  && (BaseURLWhere(st.base_url))
}

predicate isValidURI(x1: string)
{
  true
}
predicate matches___a_zA_Z0_9___(s: string)
{
  true
}

predicate RequiresShorten(st: ServiceState, url: LongURL, cand_code: ShortCode)
  reads st
{
  (ServiceStateInv(st))
  && (LongURLWhere(url))
  && (ShortCodeWhere(cand_code))
  && (isValidURI(url))
  && (cand_code !in st.store)
}
method Shorten(st: ServiceState, url: LongURL, cand_code: ShortCode) returns (code: ShortCode, short_url: string)
  modifies st
  requires ServiceStateInv(st)
  requires LongURLWhere(url)
  requires ShortCodeWhere(cand_code)
  requires isValidURI(url)
  requires cand_code !in st.store
  ensures code !in old(st.store)
  ensures st.store == old(st.store)[code := url]
  ensures short_url == old(st.base_url) + "/" + code
  ensures |st.store| == |old(st.store)| + 1
  ensures (code in st.metadata && st.metadata[code].url == url)
  ensures (code in st.metadata && st.metadata[code].click_count == 0)
  ensures code == cand_code
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

predicate RequiresResolve(st: ServiceState, code: ShortCode)
  reads st
{
  (ServiceStateInv(st))
  && (ShortCodeWhere(code))
  && (code in st.store)
}
method Resolve(st: ServiceState, code: ShortCode) returns (url: LongURL)
  modifies st
  requires ServiceStateInv(st)
  requires ShortCodeWhere(code)
  requires code in st.store
  ensures (code in old(st.store) && url == old(st.store)[code])
  ensures st.store == old(st.store)
  ensures (code in st.metadata && (code in old(st.metadata) && st.metadata[code].click_count == old(st.metadata)[code].click_count + 1))
  ensures ServiceStateInv(st)
{
  // YOUR CODE HERE
}

predicate RequiresDelete(st: ServiceState, code: ShortCode)
  reads st
{
  (ServiceStateInv(st))
  && (ShortCodeWhere(code))
  && (code in st.store)
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

predicate RequiresListAll(st: ServiceState)
  reads st
{
  (ServiceStateInv(st))
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
