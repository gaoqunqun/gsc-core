package org.gsc.net.message.gsc;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.net.message.MessageTypes;
import org.gsc.protos.Protocol;
import org.gsc.protos.Protocol.Inventory;
import org.gsc.protos.Protocol.Inventory.InventoryType;

public abstract class InventoryMessage extends GscMessage {

  protected Inventory inv;

  public InventoryMessage(byte[] data) throws Exception {
    this.data = data;
    this.inv = Protocol.Inventory.parseFrom(data);
  }

  public InventoryMessage(Inventory inv) {
    this.inv = inv;
    this.data = inv.toByteArray();
  }

  public InventoryMessage(List<Sha256Hash> hashList, InventoryType type) {
    Inventory.Builder invBuilder = Inventory.newBuilder();

    for (Sha256Hash hash :
        hashList) {
      invBuilder.addIds(hash.getByteString());
    }
    invBuilder.setType(type);
    inv = invBuilder.build();
    this.data = inv.toByteArray();
  }

  public void setType(byte type) {this.type = type;}

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  public Inventory getInventory() {
    return inv;
  }

  public MessageTypes getInvMessageType() {
    return getInventoryType().equals(InventoryType.BLOCK) ? MessageTypes.BLOCK : MessageTypes.TRANSACTION;
  }

  public InventoryType getInventoryType() {
    return inv.getType();
  }

  @Override
  public String toString() {
    Deque<Sha256Hash> hashes = new LinkedList<>(getHashList());
    StringBuilder builder = new StringBuilder();
    builder.append(super.toString()).append("invType: ").append(getInvMessageType())
        .append(", size: ").append(hashes.size())
        .append(", First hash: ").append(hashes.peekFirst());
    if (hashes.size() > 1) {
      builder.append(", End hash: ").append(hashes.peekLast());
    }
    return builder.toString();
  }

  public List<Sha256Hash> getHashList() {
    return getInventory().getIdsList().stream()
        .map(hash -> Sha256Hash.wrap(hash.toByteArray()))
        .collect(Collectors.toList());
  }

}