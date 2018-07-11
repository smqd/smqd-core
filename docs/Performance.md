# Performance

## Topic Trie Data Structure

smqd manages topics of subscribers with a Trie structure.

The below result shows the performance of the data structure that network interactions is not involved

### Test #1 (2018-Jul-11)

environment:  MacBook 15-inch 2017 (2.9GHz i7) 4 cores, 16G RAM

#### 1,000 x 1,000 topics

scenario: each client subscribes to a topic `massive/sensors/<g>/<n>/#` (g: 1~1,000, n: 1~1,000)

- all registrations: 9,567 ms.
- random topic filtering 10,000 times: 92 ms.
- removing all registrations: 10,193 ms.

#### 1,000,000 topics

scenario: each client subscribe to a topic `massive/devices/<n>/temp` (n: 1 ~ 1,000,000)

- all registrations: 14,804 ms.
- random topic filtering 10,000 times: 108 ms.
- removing all registrations: 12,210 ms.

#### counting

snapshot Trie, counts all 2M filters: 2334 ms.
