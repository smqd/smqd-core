/*
 * Created on 2005. 6. 22
 */
package com.thing2x.smqd.net.telnet;

import java.io.IOException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.wimpi.telnetd.io.BasicTerminalIO;
import net.wimpi.telnetd.io.toolkit.ActiveComponent;
import net.wimpi.telnetd.io.toolkit.BufferOverflowException;
import net.wimpi.telnetd.io.toolkit.Dimension;
import net.wimpi.telnetd.io.toolkit.InputFilter;
import net.wimpi.telnetd.io.toolkit.InputValidator;

public class BshCommandField extends ActiveComponent
{
	private static Logger logger = LoggerFactory.getLogger(BshCommandField.class);
	
	// Associations
	private InputFilter m_InputFilter = null;
	private InputValidator m_InputValidator = null;

	// Aggregations (inner class!)
	private Buffer m_Buffer;

	// Members
	private int m_Cursor = 0;
	private boolean m_InsertMode = true;
	private int m_LastSize = 0;
	private boolean m_PasswordField = false;
	private boolean m_JustBackspace;

	private int m_HistoryMax = 0;
	private int m_HistoryOffset = 0;
	private Vector<String> m_History = null;

	/**
	 * Constructs an Editfield.
	 */
	public BshCommandField(BasicTerminalIO io, String name, int length, int maxHistory)
	{
		// init superclass
		super(io, name);
		// init class params
		m_Buffer = new Buffer(length);
		setDimension(new Dimension(length, 1));
		m_Cursor = 0;
		m_InsertMode = true;

		m_HistoryMax = maxHistory;
		m_History = new Vector<String>();
	}

	/**
	 * Accessor method for field length.
	 * 
	 * @return int that represents length of editfield.
	 */
	public int getLength()
	{
		return m_Dim.getWidth();
	}

	/**
	 * Accessor method for field buffer size.
	 * 
	 * @return int that represents the number of chars in the fields buffer.
	 */
	public int getSize()
	{
		return m_Buffer.size();
	}

	public String getValue()
	{
		return m_Buffer.toString();
	}

	public void setValue(String str) throws BufferOverflowException, IOException
	{
		clear();
		append(str);
	}

	public void clear() throws IOException
	{
		positionCursorAtBegin();
		for (int i = 0; i < m_Buffer.size(); i++)
		{
			m_IO.write(' ');
		}
		positionCursorAtBegin();
		m_Buffer.clear();
		m_Cursor = 0;
		m_LastSize = 0;
		m_IO.flush();
	}

	public char getCharAt(int pos) throws IndexOutOfBoundsException
	{

		return m_Buffer.getCharAt(pos);
	}

	public void setCharAt(int pos, char ch) throws IndexOutOfBoundsException, IOException
	{
		m_Buffer.setCharAt(pos, ch);
		// cursor
		// implements overwrite mode no change
		// screen
		draw();
	}

	public void insertCharAt(int pos, char ch) throws BufferOverflowException,
			IndexOutOfBoundsException, IOException
	{

		storeSize();
		// buffer
		m_Buffer.ensureSpace(1);
		m_Buffer.insertCharAt(pos, ch);
		// cursor adjustment (so that it stays in "same" pos)
		if (m_Cursor >= pos)
		{
			moveRight();
		}
		// screen
		draw();
	}

	public void removeCharAt(int pos) throws IndexOutOfBoundsException, IOException
	{

		storeSize();
		// buffer
		m_Buffer.removeCharAt(pos);
		// cursor adjustment
		if (m_Cursor > pos)
		{
			moveLeft();
		}
		// screen
		draw();
	}

	public void insertStringAt(int pos, String str) throws BufferOverflowException,
			IndexOutOfBoundsException, IOException
	{

		storeSize();
		// buffer
		m_Buffer.ensureSpace(str.length());
		for (int i = 0; i < str.length(); i++)
		{
			m_Buffer.insertCharAt(pos, str.charAt(i));
			// Cursor
			m_Cursor++;
		}
		// screen
		draw();

	}

	public void append(char ch) throws BufferOverflowException, IOException
	{

		storeSize();
		// buffer
		m_Buffer.ensureSpace(1);
		m_Buffer.append(ch);
		// cursor
		m_Cursor++;
		// screen
		if (!m_PasswordField)
		{
			m_IO.write(ch);
		}
		else
		{
			m_IO.write('.');
		}
	}

	public void append(String str) throws BufferOverflowException, IOException
	{

		storeSize();
		// buffer
		m_Buffer.ensureSpace(str.length());
		for (int i = 0; i < str.length(); i++)
		{
			m_Buffer.append(str.charAt(i));
			// Cursor
			m_Cursor++;
		}
		// screen
		if (!m_PasswordField)
		{
			m_IO.write(str);
		}
		else
		{
			StringBuffer sbuf = new StringBuffer();
			for (int n = 0; n < str.length(); n++)
			{
				sbuf.append('.');
			}
			m_IO.write(sbuf.toString());
		}
	}

	public int getCursorPosition()
	{
		return m_Cursor;
	}

	public boolean isJustBackspace()
	{
		return m_JustBackspace;
	}

	public void setJustBackspace(boolean b)
	{
		m_JustBackspace = true;
	}

	/**
	 * @param filter Object instance that implements the InputFilter interface.
	 */
	public void registerInputFilter(InputFilter filter)
	{
		m_InputFilter = filter;
	}

	/**
	 * @param validator Object instance that implements the InputValidator
	 *        interface.
	 */
	public void registerInputValidator(InputValidator validator)
	{
		m_InputValidator = validator;
	}

	public boolean isInInsertMode()
	{
		return m_InsertMode;
	}

	public void setInsertMode(boolean b)
	{
		m_InsertMode = b;
	}

	public boolean isPasswordField()
	{
		return m_PasswordField;
	}

	public void setPasswordField(boolean b)
	{
		m_PasswordField = b;
	}

	public Vector<String> getHistory()
	{
		return m_History;
	}

	public int getHistoryOffset()
	{
		return m_HistoryOffset;
	}

	/**
	 * Method that will be reading and processing input.
	 */
	public void run() throws IOException
	{
		int historyCursor = -1;

		int in = 0;
		// m_IO.setAutoflushing(false);
		draw();
		m_IO.flush();
		do
		{
			// get next key
			in = m_IO.read();
			// Just backspace mode, convert deletes to backspace
			if (m_JustBackspace && in == BasicTerminalIO.DELETE)
			{
				in = BasicTerminalIO.BACKSPACE;
			}
			// send it through the filter if one is set
			if (m_InputFilter != null)
			{
				in = m_InputFilter.filterInput(in);
			}
			switch (in)
			{
			case -1:
				m_Buffer.clear();
				break;
			case InputFilter.INPUT_HANDLED:
				continue;
			case InputFilter.INPUT_INVALID:
				m_IO.bell();
				break;
			case BasicTerminalIO.LEFT:
				moveLeft();
				break;
			case BasicTerminalIO.RIGHT:
				moveRight();
				break;
			case BasicTerminalIO.UP:
				historyCursor++;
				if (historyCursor > m_History.size() - 1)
					historyCursor = m_History.size() - 1;
				try
				{
					if (historyCursor != -1)
						setValue(m_History.get(historyCursor));
				}
				catch (Exception e)
				{
					logger.error("History up failure", e);
				}
				break;
			case BasicTerminalIO.DOWN:
				historyCursor--;
				if (historyCursor < -1)
					historyCursor = -1;
				try
				{
					if (historyCursor == -1)
						setValue("");
					else
						setValue(m_History.get(historyCursor));
				}
				catch (Exception e)
				{
					logger.error("History down failure", e);
				}
				break;
			case BasicTerminalIO.ENTER:
				if (getCursorPosition() > 0 && getCharAt(getCursorPosition() - 1) == '\\')
				{
					setCharAt(getCursorPosition() - 1, '\n');
					break;
				}
				if (m_InputValidator != null)
				{
					if (m_InputValidator.validate(m_Buffer.toString()))
					{
						in = -1;
					}
					else
					{
						m_IO.bell();
					}
				}
				else
				{
					in = -1;
				}
				break;
			case BasicTerminalIO.BACKSPACE:
				try
				{
					removeCharAt(m_Cursor - 1);
				}
				catch (IndexOutOfBoundsException ioobex)
				{
					m_IO.bell();
				}
				break;
			case BasicTerminalIO.DELETE:
				try
				{
					removeCharAt(m_Cursor);
				}
				catch (IndexOutOfBoundsException ioobex)
				{
					m_IO.bell();
				}
				break;
			case BasicTerminalIO.TABULATOR:
				in = '\t';
			default:
				handleCharInput(in);
			}
			m_IO.flush();
		} while (in != -1);

		String str = getValue().replaceAll("[\n]", " ");
		if (str.trim().length() > 0)
		{
			m_History.insertElementAt(str, 0);
			if (m_History.size() > m_HistoryMax)
			{
				m_HistoryOffset++;
				m_History.remove(m_HistoryMax);
			}
		}
	}

	public void draw() throws IOException
	{
		int diff = m_LastSize - m_Buffer.size();
		String output = m_Buffer.toString();
		if (m_PasswordField)
		{
			StringBuffer stbuf = new StringBuffer();
			for (int n = 0; n < output.length(); n++)
			{
				stbuf.append('*');
			}
			output = stbuf.toString();
		}

		if (diff > 0)
		{
			StringBuffer sbuf = new StringBuffer();
			sbuf.append(output);
			for (int i = 0; i < diff; i++)
			{
				sbuf.append(" ");
			}
			output = sbuf.toString();
		}

		if (m_Position != null)
		{
			m_IO.setCursor(m_Position.getRow(), m_Position.getColumn());
		}
		else
		{
			m_IO.moveLeft(m_Cursor);
		}
		m_IO.write(output);

		if (m_Cursor < output.length())
		{
			m_IO.moveLeft(output.length() - m_Cursor);
		}
	}

	private void moveRight() throws IOException
	{
		// cursor
		if (m_Cursor < m_Buffer.size())
		{
			m_Cursor++;
			// screen
			m_IO.moveRight(1);
		}
		else
		{
			m_IO.bell();
		}
	}

	private void moveLeft() throws IOException
	{
		// cursor
		if (m_Cursor > 0)
		{
			m_Cursor--;
			// screen
			m_IO.moveLeft(1);
		}
		else
		{
			m_IO.bell();
		}
	}

	private void positionCursorAtBegin() throws IOException
	{
		// 1. position cursor at first char
		if (m_Position == null)
		{
			m_IO.moveLeft(m_Cursor);
		}
		else
		{
			m_IO.setCursor(m_Position.getRow(), m_Position.getColumn());
		}
	}

	private boolean isCursorAtEnd()
	{
		return (m_Cursor == m_Buffer.size());
	}

	private void handleCharInput(int ch) throws IOException
	{
		if (isCursorAtEnd())
		{
			try
			{
				// Field
				append((char) ch);
			}
			catch (BufferOverflowException bex)
			{
				m_IO.bell();
			}
		}
		else
		{
			if (isInInsertMode())
			{
				try
				{
					// Field
					insertCharAt(m_Cursor, (char) ch);
				}
				catch (BufferOverflowException bex)
				{
					m_IO.bell();
				}
			}
			else
			{
				try
				{
					// Field
					setCharAt(m_Cursor, (char) ch);
				}
				catch (IndexOutOfBoundsException bex)
				{
					m_IO.bell();
				}
			}
		}
	}

	private void storeSize()
	{
		m_LastSize = m_Buffer.size();
	}

	class Buffer
	{
		private Vector<Character> m_Buffer;
		private int m_Size;

		public Buffer(int size)
		{
			m_Buffer = new Vector<Character>(size);
			m_Size = size;
		}

		public char getCharAt(int pos) throws IndexOutOfBoundsException
		{
			return ((Character) m_Buffer.elementAt(pos)).charValue();
		}

		public void setCharAt(int pos, char ch) throws IndexOutOfBoundsException
		{

			m_Buffer.setElementAt(new Character(ch), pos);
		}

		public void insertCharAt(int pos, char ch) throws BufferOverflowException,
				IndexOutOfBoundsException
		{

			m_Buffer.insertElementAt(new Character(ch), pos);
		}

		public void append(char aChar) throws BufferOverflowException
		{

			m_Buffer.addElement(new Character(aChar));
		}

		public void append(String str) throws BufferOverflowException
		{
			for (int i = 0; i < str.length(); i++)
			{
				append(str.charAt(i));
			}
		}

		public void removeCharAt(int pos) throws IndexOutOfBoundsException
		{

			m_Buffer.removeElementAt(pos);
		}

		public void clear()
		{
			m_Buffer.removeAllElements();
		}

		public int size()
		{
			return m_Buffer.size();
		}

		public String toString()
		{
			StringBuffer sbuf = new StringBuffer();
			for (int i = 0; i < m_Buffer.size(); i++)
			{
				sbuf.append(((Character) m_Buffer.elementAt(i)).charValue());
			}
			return sbuf.toString();
		}

		public void ensureSpace(int chars) throws BufferOverflowException
		{

			if (chars > (m_Size - m_Buffer.size()))
			{
				throw new BufferOverflowException();
			}
		}
	}
}
