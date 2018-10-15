package com.thing2x.smqd.net.telnet;

import java.io.IOException;
import java.io.OutputStream;

class BshOutputStream extends OutputStream
{
  BshTerm term;

  BshOutputStream(BshTerm term)
  {
    this.term = term;
  }

  @Override
  public void write(int b) throws IOException
  {
    term.write((char) b);
  }
}