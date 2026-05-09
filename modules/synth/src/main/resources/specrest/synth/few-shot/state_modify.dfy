// Updating a counter field on a class while leaving other state untouched
class Counter {
  var count: int
}

method Increment(c: Counter)
  modifies c
  requires c.count >= 0
  ensures c.count == old(c.count) + 1
{
  c.count := c.count + 1;
}
