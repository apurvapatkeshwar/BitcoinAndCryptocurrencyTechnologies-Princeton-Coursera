import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
	double p_graph;
	double p_malicious;
	double p_txDistribution;
	int numRounds;
	boolean[] followees;
	int[] emptySender;	// no of times a sender sends no tx
	int numFollowees;
	Set<Transaction> proposedTransactions;
	Set<Transaction> consensus;
	Map<Transaction, Integer> consensusCounter;
	Map<Integer, Set<Transaction>> senderTracker;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
        this.consensus = new HashSet<>();
        this.proposedTransactions = new HashSet<>();
        this.consensusCounter = new HashMap<>();
        this.senderTracker = new HashMap<>();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        emptySender = new int[followees.length];
        numFollowees = 0;
        for (boolean followee : this.followees) {
        	if (followee) {
        		numFollowees++;
        	}
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.proposedTransactions = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
    	if (numRounds <= 0) {
    		return consensus;
    	}
    	numRounds--;
        return proposedTransactions;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
    	Map<Integer, Set<Transaction>> txsBySender = new HashMap<>();
    	for (Candidate c : candidates) {
    		if (!txsBySender.containsKey(c.sender)) {
    			txsBySender.put(c.sender, new HashSet<Transaction>());
        	}
        	txsBySender.get(c.sender).add(c.tx);
    	}
    	
    	// check empty senders
    	for (int i = 0; i < emptySender.length; i++) {
    		if (followees[i] && !txsBySender.containsKey(i)) {
    			emptySender[i]++;
    		}
    	}
    	
        for (Integer sender : txsBySender.keySet()) {
        	Set<Transaction> txs = txsBySender.get(sender);
        	if (!senderTracker.containsKey(sender)) {	// new sender found or first round
        		senderTracker.put(sender, new HashSet<Transaction>());
        	}
    		Set<Transaction> prevTxs = senderTracker.get(sender);
    		boolean txsChanged = txs.addAll(prevTxs);
    		senderTracker.put(sender, txs);		// update senderTracker
    		
    		if (!txsChanged && emptySender[sender] < 2) {	// not a malicious node
    			for (Transaction tx : txs) {
    				consensus.add(tx);
            		proposedTransactions.add(tx);
    			}
    		}        	
        }
    }
}
