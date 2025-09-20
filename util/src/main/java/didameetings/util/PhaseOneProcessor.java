package didameetings.util;

import java.util.ArrayList;
import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;

public class PhaseOneProcessor extends GenericResponseProcessor<PhaseOneReply> {

    private static final Logger LOGGER = new FancyLogger("PhaseOneProcessor");

    private final int quorum;

    private boolean accepted = true;
    private int value = -1;
    private int valballot = -1;
    private int maxballot = -1;
    private int responses = 0;
    
    private List<Integer> acceptedValues = new ArrayList<>();
    private List<Integer> acceptedValballots = new ArrayList<>();

    public PhaseOneProcessor(int quorum) {
        this.quorum = quorum;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public int getValue() {
        return this.value;
    }

    public int getValballot() {
        return this.valballot;
    }

    public int getMaxballot() {
        return this.maxballot;
    }
    
    public List<Integer> getAcceptedValues() {
        return new ArrayList<>(this.acceptedValues);
    }
    
    public List<Integer> getAcceptedValballots() {
        return new ArrayList<>(this.acceptedValballots);
    }

    @Override
    public synchronized boolean onNext(List<PhaseOneReply> allResponses, PhaseOneReply lastResponse) {
        this.responses++;
        
        // Multi-Paxos: trabalhar com listas quando protobuf compilar
        try {
            // Tentar usar métodos de lista (Multi-Paxos)
            List<Integer> responseValues = lastResponse.getValuesList();
            List<Integer> responseValballots = lastResponse.getValballotsList();
            
            LOGGER.debug("received reply {}/{} (accepted={}, maxballot={}, values={}, valballots={})", 
                this.responses, this.quorum,
                lastResponse.getAccepted(), lastResponse.getMaxballot(), 
                responseValues, responseValballots);
            
            if (!lastResponse.getAccepted()) {
                this.accepted = false;
                if (lastResponse.getMaxballot() > this.maxballot) {
                    this.maxballot = lastResponse.getMaxballot();
                }
                return true;
            }
            
            // Processar listas de valores (Multi-Paxos)
            for (int i = 0; i < responseValues.size() && i < responseValballots.size(); i++) {
                int responseValue = responseValues.get(i);
                int responseValballot = responseValballots.get(i);
                
                if (responseValballot > this.valballot) {
                    this.valballot = responseValballot;
                    this.value = responseValue;  // Para compatibilidade
                    
                    // Atualizar listas Multi-Paxos
                    if (!this.acceptedValues.contains(responseValue)) {
                        this.acceptedValues.add(responseValue);
                        this.acceptedValballots.add(responseValballot);
                    }
                    LOGGER.warn("reply had values already written, updating accepted values!");
                }
            }
            
        } catch (Exception e) {
            // Fallback para compatibilidade se protobuf ainda não compilou
            LOGGER.debug("received reply {}/{} (accepted={}, maxballot={})", 
                this.responses, this.quorum,
                lastResponse.getAccepted(), lastResponse.getMaxballot());
            
            if (!lastResponse.getAccepted()) {
                this.accepted = false;
                if (lastResponse.getMaxballot() > this.maxballot) {
                    this.maxballot = lastResponse.getMaxballot();
                }
                return true;
            }
        }
        
        return this.responses >= this.quorum;

        // BOGUS
        // this.responses++;
        // this.maxballot = lastResponse.getMaxballot();
        // this.value = lastResponse.getValue();
        // this.valballot = lastResponse.getValballot();
        // return true;
    }
}
