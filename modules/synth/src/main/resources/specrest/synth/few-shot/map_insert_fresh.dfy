// Inserting a fresh key into a map
method Insert<K(==),V>(m: map<K,V>, k: K, v: V) returns (m': map<K,V>)
  requires k !in m
  ensures m' == m[k := v]
  ensures |m'| == |m| + 1
{
  m' := m[k := v];
}
