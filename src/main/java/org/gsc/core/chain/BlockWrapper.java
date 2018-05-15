package org.gsc.core.chain;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import org.gsc.common.exception.BadItemException;
import org.gsc.common.exception.ValidateSignatureException;
import org.gsc.common.utils.MerkleTree;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.crypto.ECKey;
import org.gsc.crypto.ECKey.ECDSASignature;
import org.gsc.protos.Protocol.Block;
import org.gsc.protos.Protocol.BlockHeader;
import org.gsc.protos.Protocol.Transaction;

public class BlockWrapper extends BlockHeaderWrapper{

  private BlockId blockId;

  private Block block;

  public boolean generatedByMyself = false;

  public BlockWrapper(long timestamp, Sha256Hash parentHash, long number,  ByteString producerAddress,
      List<Transaction> transactionList) {
    super(timestamp, parentHash, number, producerAddress);

    Block.Builder blockBuild = Block.newBuilder();
    transactionList.forEach(trx -> blockBuild.addTransactions(trx));
    this.block = blockBuild.setBlockHeader(blockHeader).build();
  }

  public void addTransaction(TransactionWrapper pendingTrx) {
    this.block = this.block.toBuilder().addTransactions(pendingTrx.getInstance()).build();
  }

  public List<TransactionWrapper> getTransactions() {
    return null;
  }

  public void sign(byte[] privateKey) {
    ECKey ecKey = ECKey.fromPrivate(privateKey);
    ECDSASignature signature = ecKey.sign(getRawHash().getBytes());
    ByteString sig = ByteString.copyFrom(signature.toByteArray());

    BlockHeader blockHeader = this.block.getBlockHeader().toBuilder().setWitnessSignature(sig)
        .build();

    this.block = this.block.toBuilder().setBlockHeader(blockHeader).build();
  }

  private Sha256Hash getRawHash() {
    return Sha256Hash.ZERO_HASH;
  }

  public boolean validateSignature() throws ValidateSignatureException {
    try {
      return Arrays
          .equals(ECKey.signatureToAddress(getRawHash().getBytes(),
              ECKey.getBase64FromByteString(block.getBlockHeader().getWitnessSignature())),
              block.getBlockHeader().getRawData().getProducerAddress().toByteArray());
    } catch (SignatureException e) {
      throw new ValidateSignatureException(e.getMessage());
    }
  }

  public BlockId getBlockId() {
    return blockId;
  }

  public void setMerkleRoot() {
    List<Transaction> transactionsList = this.block.getTransactionsList();

    Vector<Sha256Hash> ids = transactionsList.stream()
        .map(TransactionWrapper::new)
        .map(TransactionWrapper::getHash)
        .collect(Collectors.toCollection(Vector::new));

    BlockHeader.raw blockHeaderRaw =
        this.block.getBlockHeader().getRawData().toBuilder()
            .setTxTrieRoot(MerkleTree.getInstance().createTree(ids).getRoot().getHash()
                .getByteString()).build();

    this.block = this.block.toBuilder().setBlockHeader(
        this.block.getBlockHeader().toBuilder().setRawData(blockHeaderRaw)).build();
  }


  public BlockWrapper(Block block) {
    super(block.getBlockHeader());
    this.block = block;
  }

  public BlockWrapper(byte[] data) throws BadItemException {
    try {
      this.block = Block.parseFrom(data);
      this.blockHeader = this.block.getBlockHeader();
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException();
    }
  }

  public byte[] getData() {
    return this.block.toByteArray();

  }

  private StringBuffer toStringBuff = new StringBuffer();

  @Override
  public String toString() {
//    toStringBuff.setLength(0);
//
//    toStringBuff.append("BlockWrapper \n[ ");
//    toStringBuff.append("hash=").append(getBlockId()).append("\n");
//    toStringBuff.append("number=").append(getNum()).append("\n");
//    toStringBuff.append("parentId=").append(getParentHash()).append("\n");
//    toStringBuff.append("witness address=")
//        .append(ByteUtil.toHexString(getWitnessAddress().toByteArray())).append("\n");
//
//    toStringBuff.append("generated by myself=").append(generatedByMyself).append("\n");
//    toStringBuff.append("generate time=").append(Time.getTimeString(getTimeStamp())).append("\n");
//
//    AtomicInteger index = new AtomicInteger();
//    if (!getTransactions().isEmpty()) {
//      toStringBuff.append("merkle root=").append(getMerkleRoot()).append("\n");
//      toStringBuff.append("txs size=").append(getTransactions().size()).append("\n");
//      toStringBuff.append("tx: {");
//      getTransactions().forEach(tx -> toStringBuff
//          .append(index.getAndIncrement()).append(":")
//          .append(tx).append("\n"));
//      toStringBuff.append("}");
//    } else {
//      toStringBuff.append("txs are empty\n");
//    }
//    toStringBuff.append("]");
//    return toStringBuff.toString();
    return "";
  }

}
