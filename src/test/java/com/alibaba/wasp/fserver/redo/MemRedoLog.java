/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.wasp.fserver.redo;

import com.alibaba.wasp.EntityGroupInfo;
import com.alibaba.wasp.plan.action.Primary;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Only for test.
 */
public class MemRedoLog extends Redo {

  public static final Log LOG = LogFactory.getLog(RedoLog.class);

  LinkedBlockingQueue<WALEdit> queue = new LinkedBlockingQueue<WALEdit>();
  Map<String, WALEdit> map = new ConcurrentHashMap<String, WALEdit>();

  private WALEdit lastAppendTransaction;

  private WALEdit lastCommitedTransaction;

  public MemRedoLog(EntityGroupInfo egi, Configuration conf) {
    super(egi, conf);
  }

  /**
   * @see java.io.Closeable#close()
   */
  @Override
  public void close() throws IOException {
  }

  /**
   * @see Redo#append(Transaction)
   */
  @Override
  public void append(Primary action, Transaction t) throws IOException {
    synchronized (this) {
      try {
        if (map.containsKey(t.getKeyStr())) {
          throw new AlreadyExsitsTransactionIdException("TransactionId:"
              + t.getTransactionID() + " already exsits.");
        }
        WALEdit edit = new WALEdit(action, t);
        queue.put(edit);
        map.put(t.getKeyStr(), edit);
        lastAppendTransaction = edit;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * @see Redo#lastCommitedTransaction()
   */
  @Override
  public WALEdit lastCommitedTransaction() throws IOException {
    return lastCommitedTransaction;
  }

  /**
   * @see Redo#lastUnCommitedTransaction()
   */
  @Override
  public WALEdit lastUnCommitedTransaction() throws InterruptedException,
      NotInitlizedRedoException, RedoLogNotServingException {
    return lastAppendTransaction;
  }

  /**
   * @see Redo#commit(Transaction)
   */
  @Override
  public boolean commit(WALEdit edit) throws IOException {
    synchronized (this) {
      // Retrieves, but does not remove, the head of this queue, or returns null
      // if this queue is empty.
      Transaction first = queue.peek().getT();
      if (first == null || !first.getKeyStr().equals(edit.getT().getKeyStr())) {
        throw new AlreadyCommitTransactionException(edit.getT());
      }
      // Retrieves and removes the head of this queue, or returns null if this
      // queue is empty.
      queue.poll();
      map.remove(first.getKeyStr());
      edit.getT().commit(edit.getT().getTransactionID());
      return true;
    }
  }

  /**
   * @see Redo#initlize()
   */
  @Override
  public void initlize() throws IOException {
  }

  @Override
  public int size() {
    return queue.size();
  }

  /**
   * @see Redo#peekLastUnCommitedTransaction()
   */
  @Override
  public WALEdit peekLastUnCommitedTransaction() throws IOException {
    return this.lastAppendTransaction;
  }

  /**
   * @see Redo#redo()
   */
  @Override
  public void redo() throws IOException {
  }
}