// A guarded state transition: advance status only from a permitted source state
datatype Status = Draft | Placed | Shipped

class Order {
  var status: Status
}

method Place(o: Order)
  modifies o
  requires o.status == Draft
  ensures o.status == Placed
{
  o.status := Placed;
}
