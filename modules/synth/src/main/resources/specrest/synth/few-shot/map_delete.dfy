// Deleting a key from a map
method Remove<K(==),V>(m: map<K,V>, k: K) returns (m': map<K,V>)
  requires k in m
  ensures k !in m'
  ensures |m'| == |m| - 1
  ensures forall j :: j in m && j != k ==> j in m' && m'[j] == m[j]
{
  m' := map j | j in m && j != k :: m[j];
}
