# Dida-Meetings Testing Guide

## Quick Start

Build and run the baseline system to demonstrate its intentional flaws.

```bash
cd DAD
source ../dev_env/env.sh
mvn clean install
```

## Running the System

Start 3 servers, console, and client:

**Terminal 1 - Server 0:**
```bash
cd server
mvn exec:java -Dexec.args="8080 0 A 10"
```

**Terminal 2 - Server 1:**
```bash
cd server
mvn exec:java -Dexec.args="8080 1 A 10"
```

**Terminal 3 - Server 2:**
```bash
cd server
mvn exec:java -Dexec.args="8080 2 A 10"
```

**Terminal 4 - Console:**
```bash
cd console
mvn exec:java -Dexec.args="localhost 8080 A"
```

**Terminal 5 - Client:**
```bash
cd app
mvn exec:java -Dexec.args="1 localhost 8080 A"
```

## Debug Commands

| Command | Effect |
|---------|--------|
| `debug 1 R` | Crash server R |
| `debug 2 R` | Freeze server R |
| `debug 3 R` | Un-freeze server R |
| `debug 4 R` | Slow-mode ON |
| `debug 5 R` | Slow-mode OFF |
| `ballot N R` | Set server R's ballot to N |

## Demonstrating the Flaws

### Base Case (should work)
```bash
# Console
ballot 0 0

# Client
open 42
add 42 100
close 42
show
```
Expected: All commands succeed, meeting 42 shows with participant 100.

### Flaw 1: Leader decides with frozen servers

The leader proceeds to Phase 2 and decides even when other servers are frozen.

Freeze servers 1 and 2:
```bash
debug 2 1
debug 2 2
```

Server 0 tries to decide alone:
```bash
open 1000
```

Watch server 0 logs: it will show "leader for ballot 0" and proceed to decide the value even though servers 1 and 2 are frozen and can't participate in the quorum. This reveals Phase 1 doesn't wait for a proper quorum.

### Flaw 2: Different decisions

Two leaders decide different values for the same instance.

Crash server 1:
```bash
debug 1 1
```

Decide instance 0 while S1 is down:
```bash
open 42
```

Restart server 1 in new terminal:
```bash
cd server
mvn exec:java -Dexec.args="8080 1 A 10"
```

Isolate server 0, make S1 leader:
```bash
debug 2 0
ballot 1 1
```

Different value for same instance:
```bash
open 43
```

Expected:
- Server 0: "Decided instance 0 with reqid=101" (OPEN 42)
- Server 1: "Decided instance 0 with reqid=201" (OPEN 43)

Same instance, different decisions. Safety violation.

### Verification

Use `show` in client to see each server's state:
```bash
show
```

Compare the three server consoles. They'll show different meetings for the same instance.