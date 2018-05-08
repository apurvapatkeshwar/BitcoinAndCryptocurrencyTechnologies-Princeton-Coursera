import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
	
	UTXOPool publicLedger;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        publicLedger = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        Set<UTXO> uniqueUTXO = new HashSet<>();
        double inputSum = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
        	Transaction.Input ip = tx.getInput(i);
    		UTXO u = new UTXO(ip.prevTxHash, ip.outputIndex);
    		
    		// no UTXO is claimed multiple times by tx
            if (uniqueUTXO.contains(u)) {
    			return false;
    		}
            uniqueUTXO.add(u);
    		
    		// all outputs claimed by tx are in the current UTXO pool
    		if (!publicLedger.contains(u)) {
    			return false;
    		}
    		Transaction.Output op = publicLedger.getTxOutput(u);
    		inputSum += op.value;
    		
    		// the signatures on each input of tx are valid
    		if (!Crypto.verifySignature(op.address, tx.getRawDataToSign(i), ip.signature)) {
    			return false;
    		}
    	}
        
        double outputSum = 0;
        boolean negativeOutputFound = false;
    	for (Transaction.Output op : tx.getOutputs()) {
    		if (op.value < 0) {
    			negativeOutputFound = true;
    		}
    		outputSum += op.value;
    	}
    	if (negativeOutputFound) {
    		return false;
    	}
    	
    	// the sum of txs input values is greater than or equal to the sum of its output
    	if (inputSum < outputSum) {
    		return false;
    	}
    	return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
    	List<Transaction> options = Arrays.asList(possibleTxs);
    	List<Transaction> accepted = new ArrayList<>();
    	List<Transaction> notAccepted = new ArrayList<>();
    	int prevSize = -1;
    	int currSize = 0;
    	while (prevSize != currSize) {
    		prevSize = currSize;
    		notAccepted = new ArrayList<>();
	        for(Transaction tx : options) {
	        	if (isValidTx(tx)) {
	        		// remove used UTXO
	            	for (Transaction.Input ip : tx.getInputs()) {
	        			UTXO u = new UTXO(ip.prevTxHash, ip.outputIndex);
	            		publicLedger.removeUTXO(u);
	        		}
	            	
	            	// add new UTXO
	            	for (int i = 0; i < tx.numOutputs(); i++) {
	            		Transaction.Output op = tx.getOutput(i);
	            		UTXO u = new UTXO(tx.getHash(), i);
	            		publicLedger.addUTXO(u, op);
	            	}
	        		accepted.add(tx);
	        	} else {
	        		notAccepted.add(tx);
	        	}
	        }
	        currSize = notAccepted.size();
	        options = notAccepted;
    	}
    	Transaction[] acceptedTransactions = new Transaction[accepted.size()];
        acceptedTransactions = accepted.toArray(acceptedTransactions);
        return acceptedTransactions;
    }

}
