import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.HashMap;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private TreeMap<Integer, LinkedList<Block>> blocksByLevel = new TreeMap<Integer, LinkedList<Block>>();
    private TreeMap<Integer, HashSet<ByteArrayWrapper>> hashesByLevel = new TreeMap<Integer, HashSet<ByteArrayWrapper>>();
    private int maxHeight = 0;
    private TransactionPool txPool = new TransactionPool();
    private HashMap<ByteArrayWrapper, UTXOPool> poolByBlock = new HashMap<ByteArrayWrapper, UTXOPool>();

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
    	// Update poolByBlock
    	UTXOPool blockPool = new UTXOPool();
    	for(Transaction tx : genesisBlock.getTransactions()) {
        	// add new UTXO
        	for (int i = 0; i < tx.numOutputs(); i++) {
        		Transaction.Output op = tx.getOutput(i);
        		UTXO u = new UTXO(tx.getHash(), i);
        		blockPool.addUTXO(u, op);
        	}
        }
    	Transaction cbTx = genesisBlock.getCoinbase();
    	for (int i = 0; i < cbTx.numOutputs(); i++) {
    		Transaction.Output op = cbTx.getOutput(i);
    		UTXO u = new UTXO(cbTx.getHash(), i);
			blockPool.addUTXO(u, op);
    	}
    	poolByBlock.put(new ByteArrayWrapper(genesisBlock.getHash()), blockPool);
    	
    	// Remove transactions from tPool
    	for (Transaction tx : genesisBlock.getTransactions()) {
    		txPool.removeTransaction(tx.getHash());
    	}
    	txPool.removeTransaction(genesisBlock.getCoinbase().getHash());
    	
    	// Update blocksByLevel
    	LinkedList<Block> bLevel = blocksByLevel.getOrDefault(maxHeight, new LinkedList<Block>());
    	bLevel.add(genesisBlock);
    	blocksByLevel.put(maxHeight, bLevel);
    	
    	// Update hashesByLevel
    	HashSet<ByteArrayWrapper> hLevel = hashesByLevel.getOrDefault(maxHeight, new HashSet<ByteArrayWrapper>());
    	hLevel.add(new ByteArrayWrapper(genesisBlock.getHash()));
    	hashesByLevel.put(maxHeight, hLevel);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
    	LinkedList<Block> maxHeightBlocks = blocksByLevel.get(blocksByLevel.lastKey());
        Iterator<Block> i = maxHeightBlocks.iterator();
        return i.next();
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        Block maxHeightBlock = getMaxHeightBlock();
        return poolByBlock.get(new ByteArrayWrapper(maxHeightBlock.getHash()));
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        TransactionPool pool = new TransactionPool(txPool);
        return pool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null) {
    		return false;
    	}
        
        ArrayList<Transaction> txList = block.getTransactions();
    	ByteArrayWrapper parentHash = new ByteArrayWrapper(block.getPrevBlockHash());
    	boolean parentFound = false;
    	int height = maxHeight;
    	while (height >= 0 && height >= maxHeight - CUT_OFF_AGE) {
    		HashSet<ByteArrayWrapper> hLevel = hashesByLevel.get(height);
    		if (hLevel.contains(parentHash)) {
    			parentFound = true;
    			break;
    		}
    		height--;
    	}
        if (!parentFound) {
        	return false;
        }
        height++;	// self_height = parent_height + 1
    	
        // Remove transactions from tPool
        TransactionPool pool = new TransactionPool(txPool);
    	for (Transaction tx : txList) {
    		pool.removeTransaction(tx.getHash());
    	}
    	pool.removeTransaction(block.getCoinbase().getHash());
        
    	// Update poolByBlock
    	TxHandler handler = new TxHandler(poolByBlock.get(parentHash));
    	Transaction[] inputTxs = new Transaction[txList.size()];
        inputTxs = txList.toArray(inputTxs);
    	Transaction[] acceptedTxs = handler.handleTxs(inputTxs);
    	if (inputTxs.length != acceptedTxs.length) {
    		return false;
    	}
    	
    	// Update txPool
    	txPool = pool;
    	
    	UTXOPool blockPool = handler.getUTXOPool();
    	Transaction cbTx = block.getCoinbase();
    	for (int i = 0; i < cbTx.numOutputs(); i++) {
    		Transaction.Output op = cbTx.getOutput(i);
    		UTXO u = new UTXO(cbTx.getHash(), i);
			blockPool.addUTXO(u, op);
    	}
    	poolByBlock.put(new ByteArrayWrapper(block.getHash()), blockPool);
    	
        // Update blocksByLevel
        LinkedList<Block> bLevel = blocksByLevel.getOrDefault(height, new LinkedList<Block>());
    	bLevel.add(block);
    	blocksByLevel.put(height, bLevel);
    	
    	// Update hashesByLevel
    	HashSet<ByteArrayWrapper> hLevel = hashesByLevel.getOrDefault(height, new HashSet<ByteArrayWrapper>());
    	hLevel.add(new ByteArrayWrapper(block.getHash()));
    	hashesByLevel.put(height, hLevel);
    	
    	// Update maxHeight
    	if (height > maxHeight) {
    		maxHeight = height;
    		if (maxHeight - CUT_OFF_AGE > 0) { // prune old level
    			HashSet<ByteArrayWrapper> hashes = hashesByLevel.get(maxHeight - CUT_OFF_AGE - 1);
    			for (ByteArrayWrapper hash : hashes) {
    				poolByBlock.remove(hash);
    			}
    			blocksByLevel.remove(maxHeight - CUT_OFF_AGE - 1);
    			hashesByLevel.remove(maxHeight - CUT_OFF_AGE - 1);
    		}
    	}
    	
    	return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
    	txPool.addTransaction(tx);
    }
}