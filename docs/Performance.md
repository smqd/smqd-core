# Performance

## Topic Trie Data Structure

smqd manages topics of subscribers with a Trie structure.

The below result shows the performance of the data structure that network interactions is not involved

> environment:  MacBook 15-inch 2017 (2.9GHz i7) 4 cores, 16G RAM

1) scenario **1K x 1K** : each client subscribes to a topic `massive/sensors/<g>/<n>/#` (g: 1 ~ 1,000, n: 1 ~ 1,000)

2) scenario **1M** : each client subscribe to a topic `massive/devices/<n>/temp` (n: 1 ~ 1,000,000)

3) snapshot&count all **2M** filters: 

#### Test: 2018-Jul-11
| Scenario     | Registration | Filtering | Unregistration | Snapshot&Count |
|--------------|--------------|-----------|----------------|----------------|
|  1K x 1K     |  9,567 ms.   |  92 ms.   |   10,193 ms.   |                |
|  1M          |  14,804 ms.  |  108 ms.  |   12,210 ms.   |   2,334 ms.    |

#### Test: 2018-Jul-12

| Scenario      | Registration | Filtering | Unregistration | Snapshot&Count |
|---------------|--------------|-----------|----------------|----------------|
| 1K x 1K       |  9,953 ms.   |  217 ms.  |    5,466 ms.   |                |
| 1M            |  14,103 ms   |  121 ms.  |    4,820 ms.   |  2,404 ms.     |
