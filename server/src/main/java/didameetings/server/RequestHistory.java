package didameetings.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

public class RequestHistory {

    private Map<Integer, RequestRecord> pending = new HashMap<>();
    private Map<Integer, RequestRecord> processed = new HashMap<>();
    private Queue<RequestRecord> topicQueue = new LinkedList<>();

    public synchronized RequestRecord getIfPending(int requestId) {
        return this.pending.get(requestId);
    }

    public synchronized RequestRecord getFirstPending() {
        if (this.pending.isEmpty()) {
            return null;
        }
        return this.pending.values().iterator().next();
    }

    public synchronized RequestRecord getIfProcessed(int requestId) {
        return this.processed.get(requestId);
    }

    public synchronized RequestRecord getIfExists(int requestId) {
        RequestRecord record = this.pending.get(requestId);
        if (record == null)
            record = this.processed.get(requestId);
        return record;
    }

    public synchronized void addToPending(int requestid, RequestRecord record) {
        this.pending.put(requestid, record);
    }

    public synchronized RequestRecord moveToProcessed(int requestid) {
        RequestRecord record = this.pending.remove(requestid);
        this.processed.put(requestid, record);
        return record;
    }

    public synchronized Collection<RequestRecord> getAllPending() {
        // devolve cópia para evitar problemas de concorrência durante a iteração
        return new ArrayList<>(this.pending.values());
    }

    public synchronized void addToTopicQueue(RequestRecord record) {
        this.topicQueue.offer(record);
    }

    public synchronized RequestRecord pollFromTopicQueue() {
        return this.topicQueue.poll();
    }

    public synchronized boolean isTopicQueueEmpty() {
        return this.topicQueue.isEmpty();
    }

    public synchronized int getTopicQueueSize() {
        return this.topicQueue.size();
    }
}
