package org.infinispan.commands.functional;

import static org.infinispan.functional.impl.EntryViews.snapshot;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.Function;

import org.infinispan.commands.Visitor;
import org.infinispan.commands.functional.functions.InjectableComponent;
import org.infinispan.commands.read.AbstractDataCommand;
import org.infinispan.commons.util.EnumUtil;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.InvocationContext;
import org.infinispan.encoding.DataConversion;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.functional.EntryView.ReadEntryView;
import org.infinispan.functional.impl.EntryViews;
import org.infinispan.functional.impl.Params;

public class ReadOnlyKeyCommand<K, V, R> extends AbstractDataCommand {

   public static final int COMMAND_ID = 62;
   protected Function<ReadEntryView<K, V>, R> f;
   protected Params params;
   protected DataConversion keyDataConversion;
   protected DataConversion valueDataConversion;

   public ReadOnlyKeyCommand(Object key, Function<ReadEntryView<K, V>, R> f, Params params,
                             DataConversion keyDataConversion,
                             DataConversion valueDataConversion,
                             ComponentRegistry componentRegistry) {
      super(key, EnumUtil.EMPTY_BIT_SET);
      this.f = f;
      this.params = params;
      this.keyDataConversion = keyDataConversion;
      this.valueDataConversion = valueDataConversion;
      this.setFlagsBitSet(params.toFlagsBitSet());
      init(componentRegistry);
   }

   public ReadOnlyKeyCommand() {
   }

   public void init(ComponentRegistry componentRegistry) {
      componentRegistry.wireDependencies(keyDataConversion);
      componentRegistry.wireDependencies(valueDataConversion);
      if (f instanceof InjectableComponent)
         ((InjectableComponent) f).inject(componentRegistry);
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public void writeTo(ObjectOutput output) throws IOException {
      output.writeObject(key);
      output.writeObject(f);
      Params.writeObject(output, params);
      DataConversion.writeTo(output, keyDataConversion);
      DataConversion.writeTo(output, valueDataConversion);
   }

   @Override
   public void readFrom(ObjectInput input) throws IOException, ClassNotFoundException {
      key = input.readObject();
      f = (Function<ReadEntryView<K, V>, R>) input.readObject();
      params = Params.readObject(input);
      this.setFlagsBitSet(params.toFlagsBitSet());
      keyDataConversion = DataConversion.readFrom(input);
      valueDataConversion = DataConversion.readFrom(input);
   }

   // Not really invoked unless in local mode
   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      CacheEntry<K, V> entry = ctx.lookupEntry(key);

      if (entry == null) {
         throw new IllegalStateException();
      }

      ReadEntryView<K, V> ro = entry.isNull() ? EntryViews.noValue((K) key, keyDataConversion) : EntryViews.readOnly(entry, keyDataConversion, valueDataConversion);
      R ret = f.apply(ro);
      return snapshot(ret);
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitReadOnlyKeyCommand(ctx, this);
   }

   @Override
   public LoadType loadType() {
      return LoadType.OWNER;
   }

   /**
    * Apply function on entry without any data
    */
   public Object performOnLostData() {
      return f.apply(EntryViews.noValue((K) key, keyDataConversion));
   }

   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder("ReadOnlyKeyCommand{");
      sb.append(", key=").append(key);
      sb.append(", f=").append(f.getClass().getName());
      sb.append(", keyDataConversion=").append(keyDataConversion);
      sb.append(", valueDataConversion=").append(valueDataConversion);
      sb.append('}');
      return sb.toString();
   }

   public DataConversion getKeyDataConversion() {
      return keyDataConversion;
   }

   public DataConversion getValueDataConversion() {
      return valueDataConversion;
   }
}
