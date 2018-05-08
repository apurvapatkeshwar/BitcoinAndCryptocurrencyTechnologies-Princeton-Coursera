import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class MaliciousNode implements Node {
	Set<Transaction> pendingTransactions;
	int numRounds;

    public MaliciousNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
    	this.numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        return;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
    	numRounds--;
    	if ((numRounds + 1) % 2 == 0) {
    		return new HashSet<Transaction>();
    	}
        return pendingTransactions;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        return;
    }
}
