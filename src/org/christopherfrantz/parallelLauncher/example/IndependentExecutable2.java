package org.christopherfrantz.parallelLauncher.example;

/**
 * This is an example for an executable that can be launched 
 * used ParallelLauncher. In this case it just waits for 
 * some time and exits. It prints eventual passed arguments 
 * to the console. IndependentExecutable1 differs in that it 
 * waits longer (simulation different runtime durations).
 * 
 * @author Christopher Frantz
 *
 */
public class IndependentExecutable2 {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Just doing IndependentExecutable2 stuff (Waiting 15s).");
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
		System.exit(0);
	}

}
