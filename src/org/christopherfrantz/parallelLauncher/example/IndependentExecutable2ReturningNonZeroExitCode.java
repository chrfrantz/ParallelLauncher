package org.christopherfrantz.parallelLauncher.example;

/**
 * Executable returning non-zero return code (i.e., error status) to test launcher handling.
 *
 * @author Christopher Frantz
 *
 */
public class IndependentExecutable2ReturningNonZeroExitCode {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Just doing IndependentExecutable2 stuff (Waiting 15s) and returning exit code 1 afterwards.");
		if(args != null && args.length > 0){
			System.out.println("Got argument(s):");
			for(int i = 0; i < args.length; i++){
				System.out.println(args[i]);
			}
		}
		//no specific activity, just waiting a little ...
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.exit(1);
	}
}
