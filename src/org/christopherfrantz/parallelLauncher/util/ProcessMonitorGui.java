package org.christopherfrantz.parallelLauncher.util;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Process Monitor GUI showing launcher activity (if activated for launcher).
 *
 * @author Christopher Frantz
 *
 */
public class ProcessMonitorGui extends JFrame {

	private JPanel contentPane;
	private JTextArea textArea;
	PipedOutputStream outPipe;

	/**
	 * Create the frame.
	 */
	public ProcessMonitorGui(String title, boolean minimizeOnStart) {
		setBounds(100, 100, 600, 400);
		this.setTitle(title);
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JScrollPane scrollPane = new JScrollPane();
		contentPane.add(scrollPane, BorderLayout.CENTER);

		textArea = new JTextArea();
		scrollPane.setViewportView(textArea);
		// ensure that scrollpanes follow end of output
		DefaultCaret caret = (DefaultCaret) textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		// key listener to redirect input to process console
		textArea.addKeyListener(new Redirect());

		// prepare redirections of outputs
		OutputStream out = new OutputStream() {
			@Override
			public void write(final int b) throws IOException {
				appendText(String.valueOf((char) b));
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				appendText(new String(b, off, len));
			}

			@Override
			public void write(byte[] b) throws IOException {
				write(b, 0, b.length);
			}
		};

		// redirect stdout and stderr to OutputStream
		System.setOut(new PrintStream(out, true));
		System.setErr(new PrintStream(out, true));

		// prepare redirection of input (link inputStream with outputStream)
		PipedInputStream ins = new PipedInputStream();
		try {
			outPipe = new PipedOutputStream(ins);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// redirect stdin
		System.setIn(ins);
		
		if(minimizeOnStart) {
			// Minimize if requested
			this.setState(Frame.ICONIFIED);
		}
	}

	public void appendText(String text) {
		if (!this.isVisible()) {
			this.setVisible(true);
		}
		textArea.append(text);
	}

	class Redirect extends KeyAdapter {

		public void keyTyped(KeyEvent e) {
			textArea.requestFocus();
			//textArea.setText(textArea.getText() + e.getKeyChar());

			try {
				//write to outpipe, which is linked with stdin
				outPipe.write(e.getKeyChar());
				outPipe.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

	}

}
