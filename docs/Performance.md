# Performance

## Topic Trie Data Structure

smqd manages topics of subscribers with a Trie structure.

The below result shows the performance of the data structure that network interactions is not involved

> environment:  MacBook 15-inch 2017 (2.9GHz i7) 4 cores (8 cores w/ hyperthreading), 16G RAM

- scenario **1K x 1K** : each client subscribes to a topic `massive/sensors/<g>/<n>/#` (g: 1 ~ 1,000, n: 1 ~ 1,000)

- scenario **1M** : each client subscribes to a topic `massive/devices/<n>/temp` (n: 1 ~ 1,000,000)

- Filtering: random topic filtering 1M times

- snapshot&count all **2M** filters:

#### Test: 2018-Jul-11
| Scenario     | Registration | Filtering  | Unregistration | Snapshot&Count |
|--------------|--------------|------------|----------------|----------------|
|  1K x 1K     |  9,567 ms.   |  9,200 ms. |   10,193 ms.   |                |
|  1M          |  14,804 ms.  | 10,800 ms. |   12,210 ms.   |   2,334 ms.    |

#### Test: 2018-Jul-12

| Scenario      | Registration | Filtering  | Unregistration | Snapshot&Count |
|---------------|--------------|------------|----------------|----------------|
| 1K x 1K       |  9,953 ms.   | 8,662 ms.  |    5,165 ms.   |                |
| 1M            |  10,380 ms   | 8,747 ms.  |    4,747 ms.   |  2,408 ms.     |

#### Test: 2018-Oct-18 (fix unregistration bug)

| Scenario      | Registration | Filtering  | Unregistration | Snapshot&Count |
|---------------|--------------|------------|----------------|----------------|
| 1K x 1K       |  9,125 ms.   | 10,219 ms. |    9,634 ms.   |                |
| 1M            |  11,629 ms   |  8,090 ms. |   12,481 ms.   |  2,379 ms.     |
