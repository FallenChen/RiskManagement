/**
 * 
 */
package com.rp.var.analytics.portfolio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.rp.var.analytics.security.options.BinomialTree;
import com.rp.var.analytics.security.options.BlackScholes;
import com.rp.var.analytics.security.options.monte_carlo.MonteCarlo;
import com.rp.var.analytics.security.options.OptionPricer;
import com.rp.var.analytics.security.options.monte_carlo.MonteCarloBinomialTree;
import com.rp.var.analytics.security.options.monte_carlo.MonteCarloBlackScholes;
import com.rp.var.model.Option;
import com.rp.var.model.Portfolio;
import com.rp.var.util.FileHelper;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.log4j.Logger;

/**
 * Implementation of the Monte Carlo Simulation model for computing VaR and pricing options.
 */
public class MonteCarloSimulation
{
    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    public static final int DEFAULT_TIME_PERIOD = 10;
    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    public static final int DEFAULT_NUMBER_OF_SIMULATIONS = 1000;
    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    private static final Logger logger_= Logger.getLogger(MonteCarloSimulation.class);

    /** Final VaR computed using this model. */
    private double            monteCarloFinalVar;
    /** Maximum VaR experienced during the simulation. */
    private double            monteCarloMaximumVar;

    /**
     * Random number generator using to generate Gaussian (Normal) and regularly distributed random
     * numbers for simulating prices.
     */
    private static final Random            rng                 = new Random();
    /** List of investments in assets. */
    private List<Double> portfolioValues;
    /** List of historical stock price data, in same order as investments. */
    private List<File>   stockPriceDataFiles;
    /** Number of assets in the portfolio. */
    private int               numberOfStocks;
    /** The confidence level to getOptionPrice VaR at. */
    private int               confidence;
    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    private final int               timePeriod;
    /** Number of simulations of stock prices, for Monte Carlo methods. */
    private final int               numberOfSimulations = DEFAULT_NUMBER_OF_SIMULATIONS;
    /** The portfolio to getOptionPrice VaR for. */
    private Portfolio portfolio;

    /**
     * Initialises a Monte Carlo simulation model using a portfolio, confidence and time period for
     * simulation.
     * 
     * @param portfolio
     * @param confidence
     * @param timePeriod
     */
    public MonteCarloSimulation( Portfolio portfolio, int confidence, int timePeriod )
    {
        this.stockPriceDataFiles = portfolio.getStockPriceDataFiles();
        this.portfolio = portfolio;
        this.portfolioValues = portfolio.getInvestments();
        this.confidence = confidence;
        this.numberOfStocks = portfolioValues.size();
        this.timePeriod = timePeriod;
    }

    /**
     * @see MonteCarloSimulation#computeForPortfolio(List)
     */
    public MonteCarloResults computeForPortfolio()
    {
        return computeForPortfolio(null);
    }

    /**
     * Computes VaR for portfolio using Monte Carlo simulation and user-defined option pricing
     * types.
     * @param allSimulatedReturns Length of allSimulatedReturns must be <code>numberOfSimulations</code> and the length of each element must be <code>portfolio.size()</code>
     * @return an array containing final and max VaRs.
     */
    MonteCarloResults computeForPortfolio(List<SimulationResults> allSimulatedReturns)
    {
        double initialPortFolioValue = 0.0;
        double finalPortfolioValue = 0.0;
        // take initial values of investments and options
        double initialOptionsValue = 0.0;

        List<Option> options = portfolio.getOptions();

        for( Option option : options )
        {
            initialOptionsValue += ( option.getInitialStockPrice() * option.getNumShares() );
        }

        initialPortFolioValue = VarUtils.sumOf( portfolio.getInvestments() ) + initialOptionsValue;

        MonteCarloResults monteCarloResults= computeForMultipleStocks( this.portfolioValues,allSimulatedReturns);

        double optionsFinalValue = 0.0, optionsMinValue = 0.0;
        for( Option o : options )
        {
            double[] finalMinPrices = new double[2];
            switch( o.getOptionStyle() )
            {
                case European:
                    MonteCarloBlackScholes monteCarloBlackScholes = new MonteCarloBlackScholes(o);
                    optionsFinalValue += monteCarloBlackScholes.getMonteCarloResults().finalValueOfOption_;
                    optionsMinValue += monteCarloBlackScholes.getMonteCarloResults().minValueOfOption_;
                    break;
                case American:
                    MonteCarloBinomialTree monteCarloBinomialTree = new MonteCarloBinomialTree(o);
                    optionsFinalValue += monteCarloBinomialTree.getMonteCarloResults().finalValueOfOption_;
                    optionsMinValue += monteCarloBinomialTree.getMonteCarloResults().minValueOfOption_;
                    break;
                default:
                    MonteCarlo monteCarlo = new MonteCarlo(o);
                    optionsFinalValue += monteCarlo.getMonteCarloResults().finalValueOfOption_;
                    optionsMinValue += monteCarlo.getMonteCarloResults().minValueOfOption_;
                    break;
            }
        }

        finalPortfolioValue = monteCarloResults.finalVaR + optionsFinalValue;
        double minPortfolioValue = monteCarloResults.maximumVaR + optionsMinValue;
        double finalVaR = initialPortFolioValue - finalPortfolioValue;
        double maxVaR = initialPortFolioValue - minPortfolioValue;
        //double[] finalMaxVaR = { Math.round( finalVaR ), Math.round( maxVaR ) };

        return new MonteCarloResults(finalVaR, maxVaR);
    }

    /**
     * Computes value at risk for either one asset or multiple assets depending on the number of
     * assets in the portfolio.
     * @param allSimulatedReturns Length of allSimulatedReturns must be <code>numberOfSimulations</code> and the length of each element must be <code>portfolio.size()</code>
    */
    MonteCarloResults computeValueAtRisk(List<SimulationResults> allSimulatedReturns)
    {
        MonteCarloResults ret = null;
        if( numberOfStocks == 1 )
        {
            double[] returnsFromFile = VarUtils.computeDailyReturns(FileHelper
                                               .getClosingPrices( stockPriceDataFiles.get( 0 ) ));
            double volatility = VarUtils
                                        .computeVolatility_EWMA( returnsFromFile );
            ret = computeForOneStock( portfolioValues.get( 0 ), volatility );
        }
        else
        {
            ret = computeForMultipleStocks( portfolioValues,allSimulatedReturns );
        }

        return ret;
    }

    /**
     * @see MonteCarloSimulation#computeForPortfolio(List)
     */
    public MonteCarloResults computeValueAtRisk()
    {
        return computeValueAtRisk(null);
    }

    /**
     * Computes the VaR for one stock using its value and volatility to run the Monte Carlo
     * simulation.
     * 
     * @param stockValue
     * @param volatility
     * @return array containing final and max vars
     */
    private MonteCarloResults computeForOneStock( double stockValue, double volatility )
    {
        double[][] stockValues = simulatePrices( stockValue, volatility, numberOfSimulations, timePeriod );

        double[] finalValues = new double[numberOfSimulations];
        double[] maximumLosses = new double[numberOfSimulations];
        for( int sim = 0 ; sim < numberOfSimulations ; sim++ )
        {
            // store stock price from last day of each simulation
            finalValues[sim] = stockValues[sim][timePeriod - 1];
            // sort the stock prices of each simulation for maximal VaR
            // calculation
            Arrays.sort( stockValues[sim] );
            maximumLosses[sim] = stockValues[sim][0];
        }

        // sort final losses for final VaR calculation
        Arrays.sort( finalValues );

        // VaR computation using final stock values
        double stockValueAtRequiredPercentile = VarUtils.getPercentile(
                                                                        finalValues, confidence );
        double finalVaR = stockValue - stockValueAtRequiredPercentile;
        /*
         * System.out.println( "Monte Carlo VaR simulated with "
         * + numberOfSimulations + " simulations of " + timePeriod
         * + " days each." );
         * System.out.println( "Monte Carlo VaR (1 stock - Final): "
         * + VarUtils.round( finalVaR ) );
         */
        this.monteCarloFinalVar = finalVaR;

        // maximum VaR during stock price path simulation
        Arrays.sort( maximumLosses );
        double maximumVaR = stockValue - maximumLosses[0];
        //double[] estimations = { finalVaR, maximumVaR };
        logger_.debug( "Monte Carlo VaR (1 stock - Maximum): "+ VarUtils.round( maximumVaR ) );

        this.monteCarloMaximumVar = maximumVaR;

        return new MonteCarloResults(finalVaR, maximumVaR);
    }

    /**
     * Computes VaR for multiple stocks using some investments provided and getting the data files
     * from the constructors.
     * 
     * @param stockValues the investments made in the assets, in same order as the stock data files.
     * @return final and minimal values simulated for the portfolio to getOptionPrice VaR from.
     */
    private MonteCarloResults computeForMultipleStocks( List<Double> stockValues, List<SimulationResults> allSimulatedReturns )
    {
        if (allSimulatedReturns != null)
        {
            assert allSimulatedReturns.size() == numberOfSimulations;
            {
                for (SimulationResults returns : allSimulatedReturns)
                {
                    assert returns.finalStockReturn.length == stockValues.size();
                    assert returns.minStockReturn.length == stockValues.size();
                }
            }

        }

        List<double[]> returnList = new ArrayList<>();

        for( File stockFile : stockPriceDataFiles )
        {
            double[] returnsFromFile = VarUtils.computeDailyReturns(FileHelper.getClosingPrices( stockFile ));
            returnList.add( returnsFromFile );
        }
        double[][] covarianceMatrix = VarUtils.generateCovarianceMatrix(
                                                                         returnList, numberOfStocks );
        double[][] decomposedMatrix = VarUtils
                                              .decomposeMatrix( covarianceMatrix );

        double[][] finalDayPrices = new double[numberOfStocks][numberOfSimulations];
        double[][] maximumLosses = new double[numberOfStocks][numberOfSimulations];

        for (int iteration = 0; iteration < numberOfSimulations ;iteration++)
        {
            // need to do this 1000 times, and then record the final prices and
            // lowest prices (highest VaR)
            SimulationResults simulatedReturnsIteration = (allSimulatedReturns== null?simulateReturns():allSimulatedReturns.get(iteration));

            double[] finalDayReturns = simulatedReturnsIteration.finalStockReturn;
            double[] minReturns = simulatedReturnsIteration.minStockReturn;

            RealMatrix L = MatrixUtils.createRealMatrix( decomposedMatrix );

            double[] correlatedFinalReturns = L.operate( finalDayReturns );
            double[] correlatedMinReturns = L.operate( minReturns );

            for( int stock = 0 ; stock < numberOfStocks ; stock++ )
            {
                // price = e^(return) * stockValue
                finalDayPrices[stock][iteration] = Math
                                                       .exp( correlatedFinalReturns[stock] )
                                                   * stockValues.get( stock );
                maximumLosses[stock][iteration] = Math
                                                      .exp( correlatedMinReturns[stock] )
                                                  * stockValues.get( stock );
            }
        }

        double[] portfolioFinalSimulatedValues = new double[numberOfSimulations];
        double[] portfolioMinSimulatedValues = new double[numberOfSimulations];

        for( int sim = 0 ; sim < numberOfSimulations ; sim++ )
        {
            double sumOfFinalStockValues = 0.0, sumOfMinStockValues = 0.0;
            for( int stock = 0 ; stock < numberOfStocks ; stock++ )
            {
                sumOfFinalStockValues += finalDayPrices[stock][sim];
                sumOfMinStockValues += maximumLosses[stock][sim];
            }
            portfolioFinalSimulatedValues[sim] = sumOfFinalStockValues;
            portfolioMinSimulatedValues[sim] = sumOfMinStockValues;
        }

        Arrays.sort( portfolioFinalSimulatedValues );

        double valueAtPercentile = VarUtils.getPercentile(
                                                           portfolioFinalSimulatedValues,
                                                           confidence );

        double portfolioValue = 0.0;

        for( double stockValue : stockValues )
        {
            portfolioValue += stockValue;
        }

        double finalVaR = portfolioValue - valueAtPercentile;

        this.monteCarloFinalVar = finalVaR;

        Arrays.sort( portfolioMinSimulatedValues );
        double maximumVaR = portfolioValue - portfolioMinSimulatedValues[0];
        logger_.debug( "Monte Carlo VaR (Portfolio - Maximum): "
                            + VarUtils.round( maximumVaR ) );
        this.monteCarloMaximumVar = maximumVaR;

        //double[] finalMinValues = { valueAtPercentile, portfolioFinalSimulatedValues[0] };

        return new MonteCarloResults(valueAtPercentile, portfolioFinalSimulatedValues[0]);

    }

    /**
     * computes the discounted value of an option in relation to the following variables
     * 
     * @param meanValue avg exercise value of option
     * @param interest
     * @param timeToMaturity
     * @return discounted value
     */
    public static double getDiscountedValue( double meanValue, double interest, double timeToMaturity )
    {
        // discount average to today = value of the option
        // PV = C / (1+interest)^numberofperiodsofinterest
        // e.g. 1000 in 5 years at 10%, PV = 1000 / (1+0.10)^5
        // if in days, convert to years
        timeToMaturity = timeToMaturity / VarUtils.DAYS_IN_YEAR;
        double denominator = Math.pow( ( 1 + interest ), timeToMaturity );
        double valueOfOption = meanValue / denominator;
        return valueOfOption;
    }

    /**
     * Simulated normally distributed prices for a stock's initial price and its volatility.
     *
     * @param stockValue
     * @param volatility
     * @return 2D array containing prices over the time period for simulation.
     */
    public static double[][] simulatePrices( double stockValue, double volatility, int numberOfSimulations, int timePeriod )
    {
        double[][] stockValues = new double[numberOfSimulations][timePeriod];

        double possibleStockValue;

        for( int sim = 0 ; sim < numberOfSimulations ; sim++ )
        {
            for( int day = 0 ; day < timePeriod ; day++ )
            {
                if( day == 0 )
                {
                    possibleStockValue = stockValue
                                         + ( volatility * rng.nextGaussian() * stockValue );
                    stockValues[sim][day] = possibleStockValue;
                }
                else
                {
                    possibleStockValue = stockValues[sim][day - 1]
                                         + ( volatility * rng.nextGaussian() * stockValues[sim][day - 1] );
                    stockValues[sim][day] = possibleStockValue;
                }
            }
        }
        return stockValues;
    }

    /**
     * Simulates normally distributed returns for each asset over the specified time period using
     * the Monte Carlo simulation model.
     *
     * @return list of returns containing simulated returns for each asset in the portfolio.
     */
    SimulationResults simulateReturns()
    {
        double[][] simulatedReturns = new double[numberOfStocks][timePeriod];
        double[] minReturns = new double[numberOfStocks];

        // simulate returns
        for( int stock = 0 ; stock < numberOfStocks ; stock++ )
        {
            for( int day = 0 ; day < timePeriod ; day++ )
            {
                simulatedReturns[stock][day] = rng.nextGaussian();
            }
        }

        double[] finalDayReturns = new double[numberOfStocks];

        // record minimum and final day returns for each stock
        for( int i = 0 ; i < numberOfStocks ; i++ )
        {
            finalDayReturns[i] = simulatedReturns[i][timePeriod - 1];

            double[] returnsForStock = Arrays.copyOf( simulatedReturns[i],
                    timePeriod );
            Arrays.sort( returnsForStock );
            minReturns[i] = returnsForStock[0];
        }

        SimulationResults simulationResults = new SimulationResults();
        simulationResults.finalStockReturn= finalDayReturns;
        simulationResults.minStockReturn= minReturns;

        return simulationResults;
    }

    /**
     * Estimates var for a certain number of days for backtesting.
     * 
     * @param numberOfDaysToTest number of days to estimate VaR over
     * @return array containing estimations of VaR for each day until the target number
     * @deprecated
     */
    double[] estimateVaRForBacktesting_OneStock( int numberOfDaysToTest )
    {
        double[] estimations = new double[numberOfDaysToTest];
        double[] returns = VarUtils.computeDailyReturns(FileHelper.getClosingPrices( stockPriceDataFiles.get( 0 ) ));
        int numberOfReturnsToUse = returns.length - 1 - numberOfDaysToTest;

        for( int day = 0 ; day < numberOfDaysToTest ; day++ )
        {
            double[] returnsToUse = Arrays.copyOf( returns, numberOfReturnsToUse );
            double volatility = VarUtils.computeVolatility_GARCH( returnsToUse );
            MonteCarloResults monteCarloResults = computeForOneStock( portfolioValues.get( 0 ), volatility );
            estimations[day] = monteCarloResults.finalVaR;
            numberOfReturnsToUse++;
        }
        return estimations;
    }

    /**
     * 
     * @return final var computed using this model.
     */
    public double getMonteCarloFinalVar()
    {
        return monteCarloFinalVar;
    }

    /**
     * 
     * @return max var simulated using this model.
     */
    public double getMonteCarloMaximumVar()
    {
        return monteCarloMaximumVar;
    }

    public static class SimulationResults
    {
        public double[] minStockReturn;
        public double[] finalStockReturn;
    }

    public static class MonteCarloResults
    {
        public final double finalVaR;
        public final double maximumVaR;

        public MonteCarloResults(double finalVaR, double maximumVaR) {
            this.finalVaR = finalVaR;
            this.maximumVaR = maximumVaR;
        }
    }

}
