# Inventory requests

## Scope

A node answers the `GetInventoryRequest` of the inventory monitor for any peer, also on an inbound
connection whose peer is unknown. To count its data for the inventory, the node goes through all its
data on the user thread. This specification defines how often a node does this work.

## Rules

- A node counts its data objects for an inventory at most once in 10 seconds. A request in between
  gets the last counts, also if it names another version than the request that was counted.
- The other values of the inventory, such as the connections and the memory, are read for each
  request.

## Rationale

For an inventory the node copied all its persistable data, with the historical stores newer than the
version that the requester names, into new maps for every request. A peer could keep the user thread
busy by sending the request in a loop. The inventory monitor requests every two minutes, so counts of
up to 10 seconds ago do not change what it shows.
