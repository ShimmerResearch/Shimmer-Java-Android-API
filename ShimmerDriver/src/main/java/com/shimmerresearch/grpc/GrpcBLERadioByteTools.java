package com.shimmerresearch.grpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import com.shimmerresearch.driverUtilities.UtilShimmer;

import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class GrpcBLERadioByteTools {

	private Process runningProcess;
	String mExeNameWindows = "ShimmerBLEGrpc.exe";
	String mExePathWindows = "C:\\Github\\Shimmer-C-API\\ShimmerAPI\\ShimmerBLEGrpc\\bin\\Debug\\" + mExeNameWindows; // Replace with the path to your .exe file
	//String exePath = "C:\\Users\\JC\\Desktop\\testgrpc\\ShimmerBLEGrpc.exe"; // Replace with the path to your .exe file

	//Below used by Consensys MacOS
	String userDir = System.getProperty("user.dir").equals("/") ? (getApplicationPath().toString() + "/libs/") : (System.getProperty("user.dir") + "/libs/");
	String mExeNameMac = "ShimmerBLEGrpc";
	String mExePathMac = userDir + "ShimmerBLEGrpc/Products/usr/local/bin/" + mExeNameMac;

  public GrpcBLERadioByteTools() {
		
	}
	
	public GrpcBLERadioByteTools(String exeName, String exePath) {
		mExeNameWindows = exeName;
		mExeNameMac = exeName;
		mExePathWindows = exePath;
		mExePathMac = exePath;
	}
	

	/**
	 * @return 
	 * @throws Exception 
	 */
	public int getFreePort() throws Exception {
		for(int i=50000;i<60000;i++) {
			try {
				// Try to create a ServerSocket on the specified port.
				// If the port is in use, this will throw an IOException.
				ServerSocket serverSocket = new ServerSocket(i);
				serverSocket.close(); // Close the socket if it was successfully created.
				return i; // Port is available.
			} catch (IOException e) {
				System.out.println("Port in use: " + i);
			}

		}
		throw new Exception("No Port Found");
	}

	public boolean isExeRunning(String exeName) {
		try {
			Process process = Runtime.getRuntime().exec("tasklist /FI \"IMAGENAME eq " + exeName + "\"");
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			while ((line = reader.readLine()) != null) {
				if (line.toLowerCase().contains(exeName.toLowerCase())) {
					return true;
				}
			}

			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
    public boolean isExeRunningMacOS(String exeName) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pgrep", "-x", exeName});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean found = reader.readLine() != null;

            if (!process.waitFor(5, TimeUnit.SECONDS)) { // Wait up to 5 seconds
                process.destroy(); // Terminate the process if it doesn't exit
                System.err.println("pgrep process timed out.");
                return false; // Consider it not found or an error
            }

            return found;
        } catch (IOException e) {
            System.err.println("Error executing pgrep command: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            System.err.println("Thread interrupted while waiting for pgrep process: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore the interrupted status
            return false;
        }
    }

	public boolean isServerRunning() {
		if(UtilShimmer.isOsMac()) {
			return isExeRunningMacOS(mExeNameMac);
		} else {
			return isExeRunning(mExeNameWindows);
		}
	}
	
	public int startServer() throws Exception {
		int port = getFreePort();

		System.out.println(port + " is free");
		List<String> command = new ArrayList<>();

		// Add the command itself
		if(UtilShimmer.isOsMac()) {
			command.add(mExePathMac);
			command.add("--port");
		} else {
			command.add(mExePathWindows);
		}
		command.add(Integer.toString(port));

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true); // Redirect standard error to the input stream
		runningProcess = processBuilder.start();
		
	    // Add shutdown hook to ensure server is closed when Java app exits
	    try {
	        Runtime.getRuntime().addShutdownHook(shutdownHook);
	    } catch (IllegalStateException ignored) {
	        // JVM is already shutting down
	    }
		
		Thread processThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println("[BLEGrpcServer] " + line);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		// Not Process.onExit(): that is Java 9+, and MATLAB loads this class on Java 8 via
		// ShimmerJavaClass.jar, where it throws NoSuchMethodError. Daemon, like onExit()'s pool thread.
		final Process process = runningProcess;
		Thread exitWatcher = new Thread(() -> {
		    try {
		        int code = process.waitFor();
		        System.err.println("[BLEGrpcServer] exited: code=" + code +
		            (code >= 128 ? " (likely signal " + (code - 128) + ")" : ""));
		    } catch (InterruptedException e) {
		        Thread.currentThread().interrupt();
		    }
		}, "BLEGrpcServer-exit");
		exitWatcher.setDaemon(true);
		exitWatcher.start();

		processThread.start();
		return port;
		// You can continue with other tasks here
	}
	
	private final Thread shutdownHook = new Thread(() -> {
	    Process p = runningProcess;
	    if (p == null) return;

	    try {
	        // Try graceful termination first
	        p.destroy(); // sends SIGTERM on Unix, WM_CLOSE/CTRL_BREAK semantics vary on Windows
	        if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
	            // Fall back to force kill
	            p.destroyForcibly();
	            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
	        }
	    } catch (InterruptedException ie) {
	        Thread.currentThread().interrupt();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	});

	public void stopServer() {
		if (runningProcess != null) {
			runningProcess.destroy();
			System.out.println("Server Stopped");
		} else {
			System.err.println("No external process is currently running.");
		}
	}
	
	public static Path getApplicationPath() {
		try {
			URL codeSourceUrl = GrpcBLERadioByteTools.class.getProtectionDomain().getCodeSource().getLocation();
			Path codeSourcePath = Paths.get(codeSourceUrl.toURI());
			if (codeSourcePath.toFile().isFile())
				return codeSourcePath.getParent(); 
			return codeSourcePath;
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Failed to determine application path due to a URI syntax error.", e);
		} 
	}

	public static void main(String[] args) {

		GrpcBLERadioByteTools grpcTools = new GrpcBLERadioByteTools();
		// TODO Auto-generated method stub
		JFrame frame = new JFrame();
		frame.getContentPane().setLayout(null);

		JButton btnNewButton = new JButton("start");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					int port = grpcTools.startServer();
					System.out.println("Server Started : " + port);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		btnNewButton.setBounds(10, 44, 224, 23);
		frame.getContentPane().add(btnNewButton);

		JButton btnStop = new JButton("stop");
		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				grpcTools.stopServer();
			}
		});
		btnStop.setBounds(10, 91, 224, 23);
		frame.getContentPane().add(btnStop);

		JButton btnCheck = new JButton("checkServerApp");
		btnCheck.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (grpcTools.isExeRunning(grpcTools.mExeNameWindows)) {
					System.out.println("EXE RUNNING");	
				} else {
					System.out.println("EXE NOT RUNNING");
				}

			}
		});
		btnCheck.setBounds(10, 139, 224, 23);
		frame.getContentPane().add(btnCheck);

		JButton button = new JButton("find free port");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int port;
				try {
					port = grpcTools.getFreePort();

					System.out.println(port + " is free");
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		button.setBounds(10, 185, 224, 23);
		frame.getContentPane().add(button);
		frame.setSize(500, 500);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	}
}
