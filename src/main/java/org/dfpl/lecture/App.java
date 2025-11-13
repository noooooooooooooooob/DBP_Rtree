package org.dfpl.lecture;

import java.util.Random;
import java.util.Scanner;

public class App {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        int ans[] = new int[3];
        Random rand = new Random();
        for(int i=0;i<3;i++)
        {
            int rd =  rand.nextInt(10);
            ans[i] = rd;
        }
        while(true)
        {
            String input =  scanner.next();
            if(input.length() != 3)
            {
                System.out.println("Wrong input");
                continue;
            }
            boolean isAcceptable = true;
            int arr[] = new int[3];
            for(int i=0;i<3;i++)
            {
                if (input.charAt(i) < '0'  || input.charAt(i) > '9')
                {
                    System.out.println("Wrong input");
                    isAcceptable = false;
                    break;
                }
                arr[i] = input.charAt(i) - '0';
            }
            if(isAcceptable)
            {
                int ball = 0, strike = 0;
                for(int i=0;i<3;i++)
                {
                    for(int j=0;j<3;j++)
                    {
                        if(ans[i] == arr[j])
                        {
                            if(i==j)
                                strike++;
                            else
                                ball++;
                        }
                    }
                }
                if(strike == 3)
                {
                    System.out.println("Correct");
                    break;
                }
                else
                {
                    System.out.println(strike + " " + ball);
                }
            }
        }
    }
}
