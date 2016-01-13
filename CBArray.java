//==================================================================================
// File: CBArray.java
//
// Description: Tool for assisting in font development for SSD1780/SSD1306 displays
//
//              Presents a matrix of checkboxes (defined by 'rows' and 'cols' below
//              Displays values such that each value presented is the hex encoded
//              value of the columns, bottom to top and left to right. These will be
//              the values necessary for displaying the character designed on one of
//              the above displays.
//
// History:
// 2015Nov26 Created & began development -- RL
// 2015Dec02 Functionally working; still needs display management cleanup (layout
//           changes with encoded value output) -- RL
// 2015Dec05 Fixed layout working -- RL
// 2015Dec10 Comment line detection working -- RL
// 2015Dec13 Integrating state machine testing completed separately -- RL
// 2015Dec16 Snapshot - reading file correctly, save dialog in progress (copy button
//           implemented) -- RL
// 2015Dec17 Implemented Record #, delRec(), insRec() (insRec without copy crashes) -- RL
// 2015Dec19 Add headers -- RL
// 2015Dec20 Mostly working for 16x16; records only saved on nextRec(); works on
//           32x32 (change CharSize.java) - must also change bit mask in displayRec()
//           to reflect cell width. ESP8266 does not seem to want to work with
//           pgm_read_word, so rebuilding output to only save series of 8 bit
//           values. Saving before starting that effort as
//           CBArrayMostlyWorking2015Dec20.java -- RL
//==================================================================================
package cbarray;

import java.io.*;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.awt.*;

import java.awt.event.*;
import javax.swing.*;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.ImageIcon;
import javax.swing.JTextField;

//import javax.swing.SwingUtilites;
import javax.swing.filechooser.*;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static fontdev.CharSize.*; // The size of this font

  //=================================================================================
  // Class: LineBuf
  // Description: Stores one line from the input file
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
class LineBuf
{
//  Boolean bCommentPresent;
//  Boolean bCharacterPresent;
  String sLine;
  public LineBuf()
  {
//    bCommentPresent = false;
//    bCharacterPresent = false;
    sLine = new String();
  }
  public LineBuf(String s)
  {
//    this.bCommentPresent = false;
//    this.bCharacterPresent = false;
    this.sLine = new String(s);
  }
} // class LineBuf

  //=================================================================================
  // Class: CharBuf
  // Description: Stores all information regarding one character cell,
  //              including related in-line comment and character encoding
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
class CharBuf
{
  String sComment;
  int aEncoding[];
  Boolean bCommentPresent;
  Boolean bCharacterPresent;
  public CharBuf()
  {
    aEncoding = new int[CharSize.rows];
  }
/*
  public CharBuf(String sComment, int aEncoding[])
  {
    this.sComment = sComment;
    this.aEncoding = aEncoding;
    this.bCommentPresent = (sComment.length() > 0) ? true : false;
  }
*/
  public CharBuf(CharBuf b)
  {
    this.sComment = b.sComment;
    this.aEncoding = b.aEncoding;
    this.bCommentPresent = b.bCommentPresent;
    this.bCharacterPresent = b.bCharacterPresent;
  }
} // class CharBuf

// State machine for parsing input file
interface State {
    public State next(Input word);

} // class CharBuf

  //=================================================================================
  // Class: Buf
  // Description: Buffer for output from state machine
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
class Buf
{
  public static int i=0;
  public static String comment;
  public static Integer[] iBuf;
  public static Boolean bCommentPresent=false;
  public static Boolean bCharacterPresent=false;
  public static StringBuffer sbCode;
  public static boolean hex;
  public static void sethex(){hex=true;}
  public static void resethex(){hex=false;}
  public static boolean gethex(){return(hex);}
  Buf(int size)
  {
    bCommentPresent = new Boolean(false);
    bCharacterPresent = new Boolean(false);
    iBuf = new Integer[size];
    for(int j=0; j<size; j++) iBuf[j]=0;
    sbCode = new StringBuffer("");
    hex = false;
    i = 0;
  }
} // class Buf

  //=================================================================================
  // Class: Input
  // Description: Input for state machine
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
class Input {
    public static String input;
    private static int current;
    char read()
    {
      if(input.length() == 0)
      {
        return 0;
      }
      else
      {
        return input.charAt(current < input.length() ? current++ : current-1);
      }

    }
    public char getcur() {return input.charAt(current); }
    public boolean endofinput() {return current >= input.length(); }
    public Input(String input)
    {
      this.input = input;
      current=0;
//System.out.println("new Input()");
    }
} // class Input

  //=================================================================================
  // enum: States
  // Description: The state machine for parsing a line of the input file
  // Input: N/A
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
enum States implements State {
//    static String sCode = new String("");
  Init
  {
    @Override
    public State next(Input word)
    {
// System.out.println("Init, evaluating "+Input.getcur());
      char c = word.read();
//      if(c == 0) return DONE;
//      else
//      {
        switch(c)
        {
          case 0: return DONENULL;
          case ' ': return SPACELAST;
          case '/': return COMMENT2;
          case '{': return PARSECODE;
          default: return Fail;
        }
//      }
    }
  },
  SPACELAST
  {
    @Override
    public State next(Input word)
    {
      char c = word.read();
//System.out.println("SPACELAST, evaluating "+c);
      switch(c)
      {
        case ' ': return SPACELAST;
        case '/': return COMMENT2;
        case '{': return PARSECODE;
        default: return Fail;
      }
    }
  },
  COMMENT2
  {
    @Override
    public State next(Input word)
    {
      char c = word.read();
//System.out.println("COMMENT2, curchar="+c);
      switch(c)
      {
        case '/':
        {
          Buf.bCommentPresent=true;
          return COMMENTLINE;
        }
        default: return Fail;
      }
    }
  },
  COMMENTLINE
  {
    @Override
    public State next(Input word)
    {
// System.out.println("COMMENTLINE, curchar="+Input.getcur());
      // Save comment as it is
      Buf.comment = Input.input;
      return DONE;
    }
  },
  PARSECODE
  {
    @Override
    public State next(Input word)
    {
      char a = word.read();
      Character b = new Character(a);
      Character c = b.toUpperCase(b);
//System.out.println("PARSECODE, Evaluating passed value "+c);
      if(Character.isWhitespace(a))
      {
        return PARSECODE; // skip whitespace
      }
      else if(Character.isDigit(a) || c == 'A' || c == 'B' || c == 'C' || c == 'D' ||
              c == 'E' || c == 'F' || c == 'X')
      {
        if(c == 'X') Buf.sethex();
        Buf.sbCode.append(a);
        return PARSECODE;
      }
      else if(c == ',' || c == '}')
      {
        // Save sCode.toInt(); 
        String s = Buf.sbCode.toString();
        Integer thiscode;
        if(Buf.gethex()) thiscode = (int)Long.parseLong(s.substring(2), 16);
        else thiscode = (int)Long.parseLong(s, 10);
        Buf.iBuf[Buf.i++] = thiscode; 
//System.out.println("PARSECODE, thiscode="+thiscode);
        Buf.resethex();
        Buf.sbCode.delete(0, Buf.sbCode.length());
        if(c == ',')
        {
          return PARSECODE;
        }
        else if(c == '}')
        {
         Buf.bCharacterPresent=true;
         return CODEDONE;
        }
        else
        {
          return Fail;
        }
      }
      else
      {
        return Fail;
      }
    }
  },
  CODEDONE
  {
    @Override
    public State next(Input word)
    {
      if(word.endofinput()) return DONE;
      char c = word.read();
//System.out.println("CODEDONE, curchar="+c);
      
      switch(c)
      {
        case ',': return CODEDONE;
        case ' ': return CODEDONE;
        case '/':
        {
          // Clear StringBuffer
          Buf.sbCode.delete(0, Buf.sbCode.length());
          // Append this to stringbuffer
          Buf.sbCode.append(c);
          return LINEENDCOMMENT;
        }
        default: return Fail;
      }
    }
  },
  LINEENDCOMMENT
  {
    @Override
    public State next(Input word) {
      char c = word.read();
//System.out.println("LINEENDCOMMENT, curchar="+c);
      if(word.endofinput())
      {
        Buf.sbCode.append(c);
        Buf.comment = Buf.sbCode.toString();
        return DONE;
      }
      else if(!Character.isISOControl(c))
      {
        Buf.sbCode.append(c);
        return LINEENDCOMMENT;
      }
      else
      {
        return Fail;
      }
    }
  },
  DONENULL
  {
    @Override
    public State next(Input word)
    {
      return DONENULL;
    }
  },
  DONE
  {
    @Override
    public State next(Input word)
    {
      char c = word.read();
//System.out.println("DONE, curchar="+c);
      return DONE;
    }
  },
  Fail
  {
    @Override
    public State next(Input word)
    {
// System.out.println("Fail, curchar="+Input.getcur());
      return Fail;
    }
  };
 
  public abstract State next(Input word);

} // enum States

// end state machine

  //=================================================================================
  // Class: CBArray
  // Description: 
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
//*********************** CBArray **********************************
// Class CBArray - the user interface
//public class CBArray extends JFrame implements ItemListener,ChangeListener
public class CBArray extends JFrame implements ItemListener,DocumentListener,ActionListener,WindowListener
{
static int rows=CharSize.rows;
static int cols=CharSize.cols;


//String[] sFileContents = new String[];
//List<String> asFileContents = new ArrayList<String>();
List<LineBuf> asFileContents = new ArrayList<LineBuf>();
//toss this? int[] iEncodings = new int[cols];

  CharBuf bChar = new CharBuf();
//  JCheckBox[][] bits = new JCheckBox[rows][cols]; 
  JToggleButton[][] bits = new JToggleButton[rows][cols]; 
  JLabel[] codes = new JLabel[rows];
  int iCurrentLine=0;
  Boolean bRecDirty=false;
  Boolean bFileDirty=false;
//  static int aN_A[] = new int[] {0,0,0,0x1f8,0x10,0x8,0x1f0,0,0x1e0,0x1c,0,0xf0,0x108,0x90,0x1f8,0};
//  static private CharBuf n_a = new CharBuf("", aN_A, false, false);

  JButton bnSetAll = new JButton("Set All");
  JButton bnClearAll = new JButton("Clear All");
  JButton bnFirstRec = new JButton("First Rec");
  JButton bnLastRec = new JButton("Last Rec");
  JButton bnPrevRec = new JButton("Prev Rec");
  JButton bnNextRec = new JButton("Next Rec");
  JButton bnInsRec = new JButton("Ins Rec");
  JButton bnDelRec = new JButton("Del Rec");
  JButton bnOpenFile = new JButton("Open File");
  JButton bnSaveFile = new JButton("Save File");
  JTextField comment = new JTextField(50);
  JCheckBox charPresent = new JCheckBox("Character Present");
  JLabel currPos = new JLabel(" Record 0/0");

  public CBArray()
  {
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

// Top panel - the current comment
    JPanel commentPanel = new JPanel(new FlowLayout());
    JLabel commentHeading = new JLabel("Comment:");
    commentPanel.add(commentHeading);
    commentPanel.add(comment);
    add(commentPanel, BorderLayout.NORTH);

// Left panel - the 'pixel' grid
    JPanel checkPanel = new JPanel(new GridLayout(rows,cols,0,0));
//checkPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
    ImageIcon pxOn = new ImageIcon("fontdev/pixelBlack.jpg");
    ImageIcon pxOff = new ImageIcon("fontdev/pixelWhite.jpg");
System.out.println("pxOn="+pxOn);
System.out.println("pxOff="+pxOff);
    for(int row=0; row<rows; row++)
    {
      for(int col=0; col<cols; col++)
      {
        if(col < cols)
        {
          bits[row][col] = new JToggleButton(pxOff);
          bits[row][col].setPreferredSize(new Dimension(15,15));
          bits[row][col].setSelectedIcon(pxOn);
          bits[row][col].setMnemonic(KeyEvent.VK_C);
          bits[row][col].setSelected(false);
          checkPanel.add(bits[row][col]);
          bits[row][col].addItemListener(this);
        }
      }
    }
JPanel pixelPanel = new JPanel();
pixelPanel.setLayout(new BoxLayout(pixelPanel, BoxLayout.LINE_AXIS));
pixelPanel.add(new Box.Filler(new Dimension(75, 1), new Dimension(150, 1), new Dimension(175, 1)));
pixelPanel.add(checkPanel);
   add(pixelPanel, BorderLayout.WEST);

// Right panel - the 'values' list
    JPanel valuePanel = new JPanel(new GridLayout(rows,1,0,0));
    for(int row=0; row<rows; row++) {
      codes[row] = new JLabel("0x00000000 ");
      valuePanel.add(codes[row]);
    }
    add(valuePanel, BorderLayout.EAST);

    comment.getDocument().addDocumentListener(this);

    bnSetAll.addActionListener(this);
    bnClearAll.addActionListener(this);
    bnFirstRec.addActionListener(this);
    bnLastRec.addActionListener(this);
    bnPrevRec.addActionListener(this);
    bnNextRec.addActionListener(this);
    bnInsRec.addActionListener(this);
    bnDelRec.addActionListener(this);
    bnOpenFile.addActionListener(this);
    bnSaveFile.addActionListener(this);

    addWindowListener(this);

    // The buttons at the bottom
    JPanel buttonPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    buttonPanel.add(bnSetAll, c);
    c.gridx = 1;
    buttonPanel.add(bnClearAll, c);
    c.gridx = 0;
    c.gridy = 1;
    buttonPanel.add(bnFirstRec, c);
    c.gridx = 1;
    buttonPanel.add(bnLastRec, c);
    c.gridx = 0;
    c.gridy = 2;
    buttonPanel.add(bnPrevRec, c);
    c.gridx = 1;
    buttonPanel.add(bnNextRec, c);
    c.gridx = 0;
    c.gridy = 3;
    buttonPanel.add(bnInsRec, c);
    c.gridx = 1;
    buttonPanel.add(bnDelRec, c);
    c.gridx = 0;
    c.gridy = 4;
    buttonPanel.add(bnOpenFile, c);
    c.gridx = 1;
    buttonPanel.add(bnSaveFile, c);
    c.gridx = 2;
    c.gridy = 3;
    buttonPanel.add(charPresent, c);
    c.gridy = 4;
    buttonPanel.add(currPos, c);
    add(buttonPanel, BorderLayout.SOUTH);

//    setSize(700,400);
pack();
    setVisible(true);

  } // CBArray()

//============================================= Listeners ================================
  //=================================================================================
  // Function: changedUpdate(DocumentEvent de)
  // Description: Listener for changes to comment field
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec20 Created -- RL
  //=================================================================================
  public void changedUpdate(DocumentEvent de)
  {
    bRecDirty = true;
  }
  public void insertUpdate(DocumentEvent de) { bRecDirty = true; }
  public void removeUpdate(DocumentEvent de) { bRecDirty = true; }
// Listens to the buttons
//  public void stateChanged(ChangeEvent ev)
  //=================================================================================
  // Function: actionPerformed(ActionEvent ae)
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void actionPerformed(ActionEvent ae)
  {
//    AbstractButton source = (AbstractButton)ae.getSource();
//    ButtonModel bmodel = source.getModel();
    Object source = ae.getSource();
//System.out.println(ev);
//    if(source == bnSetAll && bmodel.isPressed())
    if(source == bnSetAll)
    {
       updateAll(true);
//System.out.println("SetAll");
    }
//    else if(source == bnClearAll && bmodel.isPressed())
    else if(source == bnClearAll)
    {
      updateAll(false);
//System.out.println("ClearAll");
    }
    else if(source == bnFirstRec)
    {
      firstRec();
    }
    else if(source == bnLastRec)
    {
      lastRec();
    }
    else if(source == bnPrevRec)
    {
      prevRec();
    }
//    else if(source == bnNextRec && bmodel.isPressed())
    else if(source == bnNextRec)
    {
      nextRec();
    }
//    else if(source == bnInsRec && bmodel.isPressed())
    else if(source == bnInsRec)
    {
      insRec();
    }
//    else if(source == bnDelRec && bmodel.isPressed())
    else if(source == bnDelRec)
    {
      delRec();
    }
//    else if(source == bnOpenFile && bmodel.isPressed())
    else if(source == bnOpenFile)
    {
System.out.println("OpenFile(): listenercount"+((JButton) source).getActionListeners().length);

System.out.println("openFile");
      openFile();
    }
//    else if(source == bnSaveFile && bmodel.isPressed())
    else if(source == bnSaveFile)
    {
      saveFile();
    }
  } // StateChanged()

  //=================================================================================
  // Function: itemStateChanged(ItemEvent e) 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  // Listens to the checkboxes
  public void itemStateChanged(ItemEvent e)
  {
    int code;
    int col;
    int crow=0;
bRecDirty = true;
    for(col=0; col<cols; col++)
    {
      code=0;
      for(int row=rows-1; row>=0; row--)
      {
        code |= bits[row][col].isSelected() ? 1 : 0;
//System.out.println("col="+col+"; row="+row+"; code="+code);
	if(row>0) // don't shift on last pass
        {
          code <<= 1;
        }
      }
      Integer i = new Integer(code);
//      codes[crow++].setText(new String(new Integer(code).toHexString(code)));
      codes[crow++].setText(String.format("0x%8s", new Integer(code).toHexString(code)).replace(' ','0'));
      
//System.out.println("");
    }

  } // itemStateChanged

  // windowClosing
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowClosing(WindowEvent e)
  {
//    System.out.println("Application closing...");
    if((bFileDirty) && promptForSave("File"))
    {
      saveFile();
    }
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowActivated(WindowEvent e)
  {
//    System.out.println("Application activated...");
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowDeactivated(WindowEvent e)
  {
//    System.out.println("Application deactivated...");
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowIconified(WindowEvent e)
  {
//    System.out.println("Application iconifed...");
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowDeiconified(WindowEvent e)
  {
//    System.out.println("Application deiconifed...");
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowClosed(WindowEvent e)
  {
//    System.out.println("Application closed...");
  }
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  public void windowOpened(WindowEvent e)
  {
//    System.out.println("Application opened...");
  }
  
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  private void updateAll(boolean b)
  {
//System.out.println("updateAll");
    for(int row=0; row<rows; row++)
    {
      for(int col=0; col<cols; col++)
      {
        bits[row][col].setSelected(b);
      }
    }

  } // updateAll()
  
   //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
 // First record
  private void firstRec()
  {
System.out.println("firstRec();");
// Check for and process 'dirty' bit (save record if acknowledge)
    // Only move if a file has been read, and limit movement to file buffer size
    if(asFileContents.size() > 0)
    {
      iCurrentLine = 0;
      parseAndDisplayLine(iCurrentLine);
    }

  } // firstRec()

  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  // Last record
  private void lastRec()
  {
System.out.println("lastRec();");
// Check for and process 'dirty' bit (save record if acknowledge)
    // Only move if a file has been read, and limit movement to file buffer size
    if(asFileContents.size() > 0)
    {
      iCurrentLine = asFileContents.size()-1;
      parseAndDisplayLine(iCurrentLine);
    }

  } // lastRec()
  
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  // Previous record
  private void prevRec()
  {
System.out.println("prevRec();");
// Check for and process 'dirty' bit (save record if acknowledge)
    // Only move if a file has been read, and limit movement to file buffer size
    if(asFileContents.size() > 0)
    {
      iCurrentLine = iCurrentLine > 0 ? iCurrentLine-1 : iCurrentLine;
      parseAndDisplayLine(iCurrentLine);
    }

  } // prevRec()

  //=================================================================================
  // Function: nextRec
  // Description: advance to next record of current file and display it
  // Input: none
  // Output: next record is displayed
  // Returns: none (void)
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
 // Next record
  private void nextRec()
  {
System.out.println("nextRec();");
// Check for and process 'dirty' bit (save record if acknowledge)
    // Only move if a file has been read, and limit movement to file buffer size
    if(asFileContents.size() > 0)
    {
      if(bRecDirty)
      {
        if(promptForSave("Record"))
        {
// See if any pixels are set - if not, prompt for saving comment only
          if(!charPresent())
          {
            int iAnswer = JOptionPane.showConfirmDialog(this,
 //             "It appears there is no character present.\n"
              "Do you want to save the comment only?",
              "Question",
              JOptionPane.YES_NO_OPTION);
            if(iAnswer == 0) asFileContents.set(iCurrentLine, buildCommentRec());
            else asFileContents.set(iCurrentLine, buildRec());
          }
          else
          {
            asFileContents.set(iCurrentLine, buildRec());
          }
        }
        else System.out.println("nextRec(): Don't save this rec");
      }
      iCurrentLine = iCurrentLine < asFileContents.size()-1 ? iCurrentLine+1 : iCurrentLine;
      parseAndDisplayLine(iCurrentLine);
    }

  } // nextRec()
  
  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  // Insert record
  // Inserts a new record before the current character or appends at end of file
  private void insRec()
  {
System.out.println("insRec();");
    Object[] options = {"Before current record",
                        "Append at end",
                        "Cancel"};
    JCheckBox cb = new JCheckBox("Copy current record");
    Object[] params = {"Where do you want to insert a record?", cb};
    int iAnswer = JOptionPane.showOptionDialog(this, params, "Question",
                                         JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                         null, options, options[2]);
    LineBuf lb;
    if(cb.isSelected()) lb = new LineBuf(asFileContents.get(iCurrentLine).sLine);
    else lb = new LineBuf();
    
    if(iAnswer==0)
    {
System.out.println("Insert Before current; copy="+(cb.isSelected()?"Yes":"No"));
      asFileContents.add(iCurrentLine, lb);
      bFileDirty = true;
    }
    else if(iAnswer==1)
    {
System.out.println("Append at end; copy="+(cb.isSelected()?"Yes":"No"));
      asFileContents.add(lb);
      bFileDirty = true;
    }
    else System.out.println("Cancel");

    parseAndDisplayLine(iCurrentLine);

  } // insRec()

  //=================================================================================
  // Function: delRec
  // Description: Deletes current record
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  private void delRec()
  {
System.out.println("delRec();");
    Object[] options = {"Yes",
                        "No",
                        "Cancel"};
    int n = JOptionPane.showOptionDialog(this, "Do you really want to delete this record?", "Question",
                                         JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                         null, options, options[2]);
    if(n==0)
    {
System.out.println("Deleting current record");
      if(asFileContents.size() > 0)
      {
        asFileContents.remove(iCurrentLine);
        bFileDirty = true;
        // if there are still elements left after delete, check if we deleted the end array element
        if(asFileContents.size() > 0)
        {
          // If this is the last line, back up one record
          if(iCurrentLine >= asFileContents.size()) iCurrentLine--;
        }
        parseAndDisplayLine(iCurrentLine);
      }
      // Else no records to delete

    } // If user acknowledged delete
    
  } // delRec()

  //=================================================================================
  // Function:    charPresent()
  // Description: Reviews current character codes to determine if any pixels are set
  // Input:       None
  // Output:      None
  // Returns:     Boolean true if a character appears to be present; false otherwise
  // History:
  // 2015Dec20 Created -- RL
  //=================================================================================
  Boolean charPresent()
  {
    Boolean bRVal=false;
    String sHexCode;
    Long lCode;
    Integer iCode;
    for(int i=0; i<cols; i++)
    {
      sHexCode = codes[i].getText().replaceAll("\\s","");
 System.out.println("cp hxcd:\""+sHexCode+"\"");
     lCode = Long.decode(sHexCode);
      iCode = lCode.intValue();
      if(iCode != 0) bRVal=true;
    }
    return(bRVal);

  } // charPresent()
  //=================================================================================
  // Function:    buildCommentRec()
  // Description: Builds a 'comment only' line record from current display contents
  // Input:       None
  // Output:      None
  // Returns:     LineBuf: Image of record from current display contents
  // History:
  // 2015Dec20 Created -- RL
  //=================================================================================
  private LineBuf buildCommentRec()
  {
    LineBuf lb = new LineBuf();
    String s = new String(comment.getText());
System.out.println("buildCommentRec() output: \""+s+"\"");
    lb.sLine = s;
    return(lb);

  } // buildCommentRec()

  //=================================================================================
  // Function:    buildRec()
  // Description: Builds a line record from current display contents
  // Input:       None
  // Output:      None
  // Returns:     LineBuf: Image of record from current display contents
  // History:
  // 2015Dec18 Created -- RL
  // 2015Dec19 (but not yet working) -- RL
  // 2015Dec20 Tested and working -- RL
  //=================================================================================
  private LineBuf buildRec()
  {
    LineBuf lb = new LineBuf();
    String s = new String();
    s = "{";
    String sHexCode;
    Integer iCode;
    Long lCode;
    for(int i=0; i<cols; i++)
    {
      sHexCode = codes[i].getText().replaceAll("\\s","");
      lCode = Long.decode(sHexCode);
      iCode = lCode.intValue();
      bChar.aEncoding[i] = iCode;
//      String n = String.format("0x%4s", new Integer(e).toHexString(e)).replace(' ','0');
//System.out.print("n="+i+":"+sHexCode+";");
      s += sHexCode;
      if(i < cols-1) s += ',';
    }
System.out.println();
    s += "}";
    // if not last line, append a comma
    if(iCurrentLine < asFileContents.size()-1)
    {
      s += ",";
    }
    String c = new String(comment.getText());
    if(c.length() > 0) s += (" "+c);
System.out.println("buildRec() output: \""+s+"\"");
    lb.sLine = s;
    return(lb);

  } // buildRec()

  //=================================================================================
  // Function: openFile()
  // Description: Open (and read and parse) a file
  // Output: 
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  private void openFile()
  {
//bnOpenFile.setEnabled(false);
boolean foo = true;
if(foo) {
    JFileChooser chooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
      "h Files", "h");
    chooser.setFileFilter(filter);
    int rval = chooser.showOpenDialog(null);
//    String str = new String();
//    Pattern p = Pattern.compile("^\\s*//.*");
    if(rval == JFileChooser.APPROVE_OPTION)
    {
      System.out.println("File:"+chooser.getSelectedFile().getName());
      Scanner s = null;
      LineBuf buf = null;
      try
      {
        String sFullPathToFile = new String(chooser.getCurrentDirectory()+"/"+chooser.getSelectedFile().getName());
//        s = new Scanner(new BufferedReader(new FileReader(chooser.getSelectedFile().getName())));
        s = new Scanner(new BufferedReader(new FileReader(sFullPathToFile)));
        while(s.hasNextLine())
        {
          buf = new LineBuf();
          buf.sLine = s.nextLine();
//          Matcher m = p.matcher(buf.sLine);
//          // Comment lines are simple to parse/find - do that now
//          buf.iType = m.matches() ? 1 : 0;
//          System.out.println("OpenFile:"+buf.sLine);
//            parseLine(s.nextLine());
//          asFileContents.add(s.nextLine());
          asFileContents.add(buf);
        }
        bFileDirty = false;
      }
      catch(FileNotFoundException ex)
      {
        System.out.println(ex);
      }
      finally
      {
        if(s != null)
        {
          s.close();
        }
      }
    }
    // See if file was opened and read
    // If so, parse and display the first line
    if(asFileContents.size() > 0)
    {
      iCurrentLine = 0;
      parseAndDisplayLine(iCurrentLine);
    }
} // if(foo) (debug)
//bnOpenFile.setEnabled(true);

  } // openFile()

  //=================================================================================
  // Function: parseAndDisplayLine()
  // Description: Parse and display a line from the current file
  // Input: int l: line number to display
  // Output: 
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  private void parseAndDisplayLine(int l)
  {
    State s;
    Buf b = new Buf(cols);
    Input in = new Input(asFileContents.get(l).sLine);
//    for(s = States.Init; s != null && s != States.Fail && s != States.DONE; s = s.next(in))
    for(s = States.Init; s != null && s != States.Fail && s != States.DONE && s != States.DONENULL; s = s.next(in))
    {
//System.out.println("parseAndDisplayLine(): Next Char:"+in.getcur());
    }

if(s == States.DONENULL)
{
  Buf.bCommentPresent = false;
  Buf.bCharacterPresent = false;
  Buf.comment = "";
  for(int col=0; col<cols; col++) Buf.iBuf[col]=0;
System.out.println("DoneNull");
}

    if(s == States.DONE || s == States.DONENULL)
    {
      // File read and parsed correctly; display it
      bChar.bCommentPresent = Buf.bCommentPresent;
      bChar.bCharacterPresent = Buf.bCharacterPresent;
      bChar.sComment = Buf.comment;
System.out.println("Parse: BufComment="+Buf.comment);
      for(int col = 0; col < cols && Buf.iBuf[col] != null; col++)
      {
        bChar.aEncoding[col] = Buf.iBuf[col];
//int code = Buf.iBuf[col];
//System.out.println("parse/code:"+new Integer(code).toHexString(code));
      }

      displayRec();
      bRecDirty=false;
    }
    else
    {
      System.out.println("parseAndDisplayLine(): Couldn't parse input file. Final state="+s);
    }

  } // parseAndDisplayLine()

  //=================================================================================
  // Function: displayRec()
  // Description: display a record from file
  // Output: 
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  private void displayRec()
  {
    int code;
//    int ctmp;
    int col;
    int crow=0;
    for(col=0; col<cols; col++)
    {
      code=bChar.aEncoding[col];
//System.out.println("disprec; code="+code);
      for(int row=rows-1; row>=0; row--)
      {
//        ctmp = code & 0x1;
        bits[row][col].setSelected((code & 0x80000000) != 0 ? true : false);
//System.out.println("disprec col="+col+"; row="+row+"; code="+code);
	if(row>0) // don't shift on last pass
        {
          code <<= 1;
        }
      }
      Integer i = bChar.aEncoding[col];
//      codes[crow++].setText(new String(new Integer(code).toHexString(code)));
// 16 col/32 col characters: change following format string to match column width
if(cols == 16)
{
String s = String.format("0x%4s", new Integer(i).toHexString(i)).replace(' ','0');
}
else if(cols == 32)
{
String s = String.format("0x%8s", new Integer(i).toHexString(i)).replace(' ','0');
}
//System.out.println("displayRec:"+s);
      // Update the comment field
      comment.setText(bChar.sComment);
//System.out.println("displayRec comment:"+bChar.sComment);

      // Update the 'character present' indication
      charPresent.setSelected(bChar.bCharacterPresent);
      // Update the displayed file position
      String s2 = new String(" Record "+(iCurrentLine+1)+"/"+asFileContents.size());
      currPos.setText(s2);
//      codes[crow++].setText(String.format("0x%4s", new Integer(code).toHexString(code)).replace(' ','0'));
//      codes[crow++].setText(s);
//          System.out.println("Code["+i+"]="+String.format("0x%4s", Integer.toHexString(n)).replace(' ', '0'));
    }
    
  } // displayRec()

  //=================================================================================
  // Function: promptForSave(String s)
  // Description: prompt for save
  // Input: String: Item to prompt for (e.g. "Record" or "File")
  // Output: 
  // History:
  // 2015Dec18 Created -- RL
  //=================================================================================
  Boolean promptForSave(String s)
  {
    Boolean bRval=false;
    Object[] options = {"Yes",
                        "No",
                        "Cancel"};
    int iAnswer = JOptionPane.showOptionDialog(this, s+" has changed. Save?", "Question",
                                         JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                         null, options, options[2]);
    if(iAnswer==0)
    {
      bRval=true;
    }
    return(bRval);

  } // promptForSaveRec()

  //=================================================================================
  // Function: 
  // Description:
  // Input: 
  // Output:
  // Returns:
  // History:
  // 2015Dec Created -- RL
  //=================================================================================
  // Save file
  private void saveFile()
  {
System.out.println("saveFile();");
    LineBuf buf = new LineBuf();
//System.out.println("Save; FileDirty="+bFileDirty);
   // Make an inital pass over file adjusting the line ends 
   for(int i=0; i<asFileContents.size(); i++)
    {
      buf = asFileContents.get(i);
//  System.out.println("Save;("+i+"):"+" CommentPresent:"+buf.bCommentPresent+
//                     " CharPresent:"+buf.bCharacterPresent+"  Line:"+buf.sLine);
//System.out.println("Save raw;("+i+"): Line:"+buf.sLine);
      if(i < asFileContents.size()-1)
      {
        if(buf.sLine.contains("}") && !buf.sLine.contains("},"))
        {
          String s = buf.sLine.replaceAll("}","},");
          buf.sLine = s;
//System.out.println("Save adj;("+i+"): Line:"+buf.sLine);
        }
      }
      else if(i == asFileContents.size() -1)
      {
        if(buf.sLine.contains("},"))
        {
          buf.sLine.replaceAll("},","}");
 //System.out.println("Save adj last;("+i+"): Line:"+buf.sLine);
        }
      }
    }
    // Then save the file...
    JFileChooser chooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter("h Files", "h");
    chooser.setFileFilter(filter);
    int rval = chooser.showSaveDialog(null);
    if(rval == JFileChooser.APPROVE_OPTION)
    {
      try
      {
        String sFullPathToFile = new String(chooser.getCurrentDirectory()+"/"+chooser.getSelectedFile().getName());
System.out.println("Saving to:\""+sFullPathToFile+"\"");
        PrintWriter writer = new PrintWriter(sFullPathToFile);
        for(int i=0; i<asFileContents.size(); i++)
        {
          writer.println(asFileContents.get(i).sLine);
        }
        writer.close();
saveFileAs8Bit(sFullPathToFile);
      }
      catch(FileNotFoundException ex)
      {
System.out.println(ex);
      }
      finally
      {
      }
    }
  } // saveFile

  //=================================================================================
  // Function:    saveFileAs8Bit
  // Description: Since ESP8266 pgm_read_word() does not appear to work, but
  //                pgm_read_byte() does, this function produces an additional output
  //                file that is 'byte split' rather than 16 or 32 bit 'word split'
  //
  // Input:       String sFilename
  // Output:      Writes byte split output file with 'b' appended to incoming filename
  // Returns:
  // History:
  // 2015Dec20 Created -- RL
  //=================================================================================
  // Save file using only 8 bit values
  private void saveFileAs8Bit(String sFullPathToFile)
  {
    StringBuffer sbFullPathToOutputFile = new StringBuffer(sFullPathToFile);
System.out.println("saveFileAs8Bit();");
// Assuming asFileContents has already been parsed and cleaned up above....
    int p;
    LineBuf buf = new LineBuf();
    StringBuilder sbOutbuf;
    // Convert from 16 or 32 bit words to 8 bit values
    for(int i=0; i<asFileContents.size(); i++)
    {
      buf = asFileContents.get(i);
      sbOutbuf = new StringBuilder(); // clear the output buffer
// Algorithm:
// Call this function to generate byte output files (append '8' to filename) in addition
// to the normal output files every time a file is saved
// buf.sLine has current line
// find '{'
      p = buf.sLine.indexOf('{');
      // Copy all of this to the output buffer
System.out.println("buf.sLine:"+buf.sLine);
if(p > -1) System.out.println("sfa8b p="+p);
      if(p>0) sbOutbuf.append(buf.sLine.substring(0,p));
      else sbOutbuf.append("{");
// then parse each column value...
//String[] hexnumstrings = buf.sLine.split(",");
      for(int col=0; col<cols; col++)
      {
        // find first "0x" of this output 16 or 32 bit word
        p = buf.sLine.indexOf("0x", p);
if(p > -1) System.out.println("sfa8b#2 p="+p);
        sbOutbuf.append(buf.sLine.substring(p, p+4));
        sbOutbuf.append(",");
        p+=4;
// have first byte - now process one more for 16 rows, or 3 more for 32 rows
// ...and split each into 2 bytes (16 rows) or 4 bytes (32 rows), saving to new string
        for(int byt=1; byt<(rows/8); byt++)
        {
System.out.println("foreachbyte-"+byt+":"+sbOutbuf.toString());
          sbOutbuf.append("0x");
          sbOutbuf.append(buf.sLine.substring(p, p+2));
          sbOutbuf.append(",");
          p += 2;
        }
//        if(col < cols) sbOutbuf.append(",");
      } // endfor each column
      if(i<asFileContents.size()-1) // if not last line
      {
        sbOutbuf.append("},");
        if(buf.sLine.substring(buf.sLine.indexOf("}")).length() > 2)
        {
          sbOutbuf.append(buf.sLine.substring(p+2)); // append comment if present
        }
      }
      else
      {
        sbOutbuf.append("}");
        if(buf.sLine.substring(buf.sLine.indexOf("}")).length() > 2)
        {
          sbOutbuf.append(buf.sLine.substring(p+2)); // append comment if present
        }
      } // endelse last line

      // replace original sLine with new string
      buf.sLine = sbOutbuf.toString();

    } // endfor each line in file
    

    // Then save the file...
    try
    {
      sbFullPathToOutputFile.append("8bit");
System.out.println("Saving 8 bit only to:\""+sbFullPathToOutputFile.toString()+"\"");
      PrintWriter writer = new PrintWriter(sbFullPathToOutputFile.toString());
      for(int i=0; i<asFileContents.size(); i++)
      {
        writer.println(asFileContents.get(i).sLine);
      }
      writer.close();
    }
    catch(FileNotFoundException ex)
    {
System.out.println(ex);
    }
    finally
    {
    }
  } // saveFileAs8Bit()


  public static void main(String[] args) throws FileNotFoundException
  {
    new CBArray();

  } // main()

} //  class CBArray

