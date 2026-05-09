// Updating the value at an existing key — cardinality is preserved
method Update<K(==),V>(m: map<K,V>, k: K, v: V) returns (m': map<K,V>)
  requires k in m
  ensures m' == m[k := v]
  ensures |m'| == |m|
{
  m' := m[k := v];
}
