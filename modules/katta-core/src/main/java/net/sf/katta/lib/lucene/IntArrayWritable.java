package net.sf.katta.lib.lucene;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.IntWritable;

public class IntArrayWritable extends ArrayWritable {
  public IntArrayWritable() {
    super(IntWritable.class);
  }
  
  public IntArrayWritable(IntWritable[] values) {
    super(IntWritable.class, values);
  }
}
