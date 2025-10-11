
# Default recipe - show available commands
default:
    @just --list

# Show help with examples
help:
    @echo "DAD - DidaMeetings Project Commands"
    @echo "==================================="
    @echo ""
    @echo "Servers:"
    @echo "  just server-A-0    # Start server A replica 0 on port 8000"
    @echo "  just server-A-1    # Start server A replica 1 on port 8000"
    @echo "  just server-A-2    # Start server A replica 2 on port 8000"
    @echo "  just server-B-0    # Start server B replica 0 on port 8001"
    @echo "  just server-B-1    # Start server B replica 1 on port 8001"
    @echo "  just server-B-2    # Start server B replica 2 on port 8001"
    @echo ""
    @echo "Clients:"
    @echo "  just client        # Start client connecting to localhost:8000 config A"
    @echo "  just console       # Start console connecting to localhost:8000 config A"
    @echo ""
    @echo "Build:"
    @echo "  just build         # Build the project"
    @echo "  just clean         # Clean the project"

# Build the project
build:
    mvn clean install

# Clean the project
clean:
    mvn clean

# Start servers with scheduler A
server-A-0:
    @cd server && mvn exec:java -Dexec.args="8000 0 A 2"

server-A-1:
    @cd server && mvn exec:java -Dexec.args="8000 1 A 2"

server-A-2:
    @cd server && mvn exec:java -Dexec.args="8000 2 A 2"

# Start servers with scheduler B
server-B-0:
    @cd server && mvn exec:java -Dexec.args="8001 0 B 2"

server-B-1:
    @cd server && mvn exec:java -Dexec.args="8001 1 B 2"

server-B-2:
    @cd server && mvn exec:java -Dexec.args="8001 2 B 2"

server-B-3:
    @cd server && mvn exec:java -Dexec.args="8001 3 B 2"

server-B-4:
    @cd server && mvn exec:java -Dexec.args="8001 4 B 2"

server-B-5:
    @cd server && mvn exec:java -Dexec.args="8001 5 B 2"

# Start client
client:
    @cd app && mvn exec:java -Dexec.args="1 localhost 8000 A"

# Start console
console:
    @cd console && mvn exec:java -Dexec.args="localhost 8000 A"
