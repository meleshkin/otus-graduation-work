- GET  ws://localhost:8080/ws/filter/1 
---
- GET  http://localhost:8080/filter/1/act 
- GET  http://localhost:8080/filter/1 
- GET  http://localhost:8080/filter 
- POST http://localhost:8080/filter 
```
{
"classType":"AlertRemoved",
"probableCause": "Link down",
"specificCause": "eth1 link down",
"severity": "MAJOR",
"nodeId": 1,
"dateTimeModified": 1711285461
}
```