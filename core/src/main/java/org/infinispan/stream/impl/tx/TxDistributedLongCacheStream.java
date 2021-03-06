package org.infinispan.stream.impl.tx;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.infinispan.commons.util.IntSet;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.impl.LocalTxInvocationContext;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.remoting.transport.Address;
import org.infinispan.stream.impl.AbstractCacheStream;
import org.infinispan.stream.impl.DistributedCacheStream;
import org.infinispan.stream.impl.DistributedDoubleCacheStream;
import org.infinispan.stream.impl.DistributedIntCacheStream;
import org.infinispan.stream.impl.DistributedLongCacheStream;

/**
 * Long variant of tx cache stream
 * @see TxDistributedCacheStream
 */
public class TxDistributedLongCacheStream extends DistributedLongCacheStream {
   private final Address localAddress;
   private final LocalTxInvocationContext ctx;
   private final ConsistentHash hash;

   TxDistributedLongCacheStream(AbstractCacheStream stream, Address localAddress, ConsistentHash hash,
           LocalTxInvocationContext ctx) {
      super(stream);
      this.localAddress = localAddress;
      this.ctx = ctx;
      this.hash = hash;
   }

   @Override
   protected Supplier<Stream<CacheEntry>> supplierForSegments(ConsistentHash ch, IntSet targetSegments,
           Set<Object> excludedKeys, boolean primaryOnly) {
      return () -> {
         Supplier<Stream<CacheEntry>> supplier = super.supplierForSegments(ch, targetSegments, excludedKeys, primaryOnly);
         Set<CacheEntry> set = ctx.getLookedUpEntries().values().stream()
                                  .filter(e -> !isPrimaryOwner(ch, e))
                                  .collect(Collectors.toSet());
         Stream<CacheEntry> suppliedStream = supplier.get();
         if (!set.isEmpty()) {
            return Stream.concat(set.stream(), suppliedStream);
         }
         return suppliedStream;
      };
   }

   @Override
   protected <R> DistributedCacheStream<R> cacheStream() {
      return new TxDistributedCacheStream<R>(this, localAddress, hash, ctx);
   }

   @Override
   protected DistributedIntCacheStream intCacheStream() {
      return new TxDistributedIntCacheStream(this, localAddress, hash, ctx);
   }

   @Override
   protected DistributedDoubleCacheStream doubleCacheStream() {
      return new TxDistributedDoubleCacheStream(this, localAddress, hash, ctx);
   }
}
