/**
 * 
 */
package com.rp.risk_management.analytics.portfolio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.rp.risk_management.analytics.security.options.monte_carlo.MonteCarlo;
import com.rp.risk_management.analytics.security.options.monte_carlo.MonteCarloBinomialTree;
import com.rp.risk_management.analytics.security.options.monte_carlo.MonteCarloBlackScholes;
import com.rp.risk_management.marketdata.model.Quote;
import com.rp.risk_management.model.Option;
import com.rp.risk_management.model.Portfolio;
import com.rp.risk_management.util.FileHelper;
import com.rp.risk_management.util.model.PortfolioUtil;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.log4j.Logger;

/**
 * Implementation of the Monte Carlo Simulation model for computing VaR and pricing options.
 */
public class MonteCarloSimulation
{
    private static final Logger logger_= Logger.getLogger(MonteCarloSimulation.class);

    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    public static final int DEFAULT_TIME_PERIOD = 10;
    /** Number of days to simulate prices over and getOptionPrice VaR for. */
    public static final int DEFAULT_NUMBER_OF_SIMULATIONS = 1000;

    /** List of investments in assets. */
    private List<Double> portfolioValues;
    /** List of historical stock price data, in same order as investments. */
    private List<List<Quote>> allStockQuotes_;
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
        this.allStockQuotes_ = PortfolioUtil.getStockQuotes(portfolio);
        this.portfolio = portfolio;
        this.portfolioValues = PortfolioUtil.getAssetInvestment(portfolio);
        this.confidence = confidence;
        this.numberOfStocks = portfolioValues.size();
        this.timePeriod = timePeriod;
    }

    /**
     * @see MonteCarloSimulation#computeValueAtRiskForPortfolio(List)
     */
    public MonteCarloResults computeValueAtRiskForPortfolio()
    {
        return computeValueAtRiskForPortfolio(null);
    }

    /**
     * Computes VaR for portfolio using Monte Carlo simulation and user-defined option pricing
     * types.
     * @param allSimulatedReturns Length of allSimulatedReturns must be <code>numberOfSimulations</code> and the length of each element must be <code>portfolio.size()</code>
     * @return an array containing final and max VaRs.
     */
    public MonteCarloResults computeValueAtRiskForPortfolio(List<com.rp.risk_management.analytics.simulation.MonteCarlo.SimulationResults> allSimulatedReturns)
    {
        double initialPortFolioValue = 0.0;
        double finalPortfolioValue = 0.0;

        MonteCarloResults monteCarloResults= computeForMultipleStocks( this.portfolioValues,allSimulatedReturns);
        double optionsFinalValue = 0.0, optionsMinValue = 0.0;
        // take initial values of investments and options
        double initialOptionsValue = 0.0;

        if (portfolio.getOptions() != null) {
            List<Option> options = portfolio.getOptions();



            for (Option option : portfolio.getOptions()) {
                initialOptionsValue += (option.getInitialStockPrice() * option.getNumShares());
            }

            for (Option o : options) {
                double[] finalMinPrices = new double[2];
                switch (o.getOptionStyle()) {
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
        }

        initialPortFolioValue = VarUtils.sumOf(PortfolioUtil.getAssetInvestment(portfolio)) + initialOptionsValue;
        finalPortfolioValue = monteCarloResults.finalVaR + optionsFinalValue;
        double minPortfolioValue = monteCarloResults.maximumVaR + optionsMinValue;
        double finalVaR = initialPortFolioValue - finalPortfolioValue;
        double maxVaR = initialPortFolioValue - minPortfolioValue;

        return new MonteCarloResults(finalVaR, maxVaR);
    }

    /**
     * Computes value at risk for either one asset or multiple assets depending on the number of
     * assets in the portfolio.
     * @param allSimulatedReturns Length of allSimulatedReturns must be <code>numberOfSimulations</code> and the length of each element must be <code>portfolio.size()</code>
    */
    private MonteCarloResults computeValueAtRisk(List<com.rp.risk_management.analytics.simulation.MonteCarlo.SimulationResults> allSimulatedReturns)
    {
        MonteCarloResults ret = null;
        if( numberOfStocks == 1 )
        {
            double[] returnsFromFile = VarUtils.computeDailyReturns(FileHelper
                                               .getClosingPrices( allStockQuotes_.get( 0 ) ));
            double volatility = VarUtils.computeVolatility_EWMA( returnsFromFile );
            ret = computeForOneStock( portfolioValues.get( 0 ), volatility );
        }
        else
        {
            ret = computeForMultipleStocks( portfolioValues,allSimulatedReturns );
        }

        return ret;
    }

    /**
     * @see MonteCarloSimulation#computeValueAtRiskForPortfolio(List)
     */
    public MonteCarloResults computeValueAtRisk()
    {
        return computeValueAtRisk(null);
    }

    private MonteCarloResults computeForOneStock( double stockValue, double volatility)
    {
        return computeForOneStock(stockValue,volatility,
            com.rp.risk_management.analytics.simulation.MonteCarlo.simulatePrices( stockValue, volatility, numberOfSimulations, timePeriod ));
    }

    /**
     * Computes the VaR for one stock using its value and volatility to run the Monte Carlo
     * simulation.
     * 
     * @param stockValue
     * @param volatility
     * @return array containing final and max vars
     */
    private MonteCarloResults computeForOneStock( double stockValue, double volatility, double[][] stockValues )
    {
        if (stockValues != null)
        {
            assert stockValues.length == numberOfSimulations;
            for (int i = 0; i < stockValues.length; i++)
                assert stockValues[i].length == timePeriod;
        }
        else {
            stockValues = com.rp.risk_management.analytics.simulation.MonteCarlo.simulatePrices(stockValue, volatility, numberOfSimulations, timePeriod);
        }
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

        // maximum VaR during stock price path simulation
        Arrays.sort( maximumLosses );
        double maximumVaR = stockValue - maximumLosses[0];
        logger_.debug( "Monte Carlo VaR (1 stock - Maximum): "+ VarUtils.round( maximumVaR ) );

        return new MonteCarloResults(finalVaR, maximumVaR);
    }

    /**
     * Computes VaR for multiple stocks using some investments provided and getting the data files
     * from the constructors.
     * 
     * @param stockValues the investments made in the assets, in same order as the stock data files.
     * @return final and minimal values simulated for the portfolio to getOptionPrice VaR from.
     */
    private MonteCarloResults computeForMultipleStocks( List<Double> stockValues, List<com.rp.risk_management.analytics.simulation.MonteCarlo.SimulationResults> allSimulatedReturns )
    {
        if (allSimulatedReturns != null)
        {
            assert allSimulatedReturns.size() == numberOfSimulations;
            {
                for (com.rp.risk_management.analytics.simulation.MonteCarlo.SimulationResults returns : allSimulatedReturns)
                {
                    assert returns.finalStockReturn.length == stockValues.size();
                    assert returns.minStockReturn.length == stockValues.size();
                }
            }

        }

        List<double[]> returnList = new ArrayList<>();

        for( List<Quote> stockQuotes : allStockQuotes_)
        {
            double[] returnsFromFile = VarUtils.computeDailyReturns(FileHelper.getClosingPrices( stockQuotes ));
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
            com.rp.risk_management.analytics.simulation.MonteCarlo.SimulationResults simulatedReturnsIteration = (allSimulatedReturns== null? com.rp.risk_management.analytics.simulation.MonteCarlo.simulateReturns(numberOfStocks,timePeriod):allSimulatedReturns.get(iteration));

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

        Arrays.sort( portfolioMinSimulatedValues );
        double maximumVaR = portfolioValue - portfolioMinSimulatedValues[0];
        logger_.debug( "Monte Carlo VaR (Portfolio - Maximum): "
                            + VarUtils.round( maximumVaR ) );

        return new MonteCarloResults(valueAtPercentile, portfolioFinalSimulatedValues[0]);

    }

    /**
     * Estimates risk_management for a certain number of days for backtesting.
     * 
     * @param numberOfDaysToTest number of days to estimate VaR over
     * @return array containing estimations of VaR for each day until the target number
     */
    double[] estimateVaRForBacktesting_OneStock( int numberOfDaysToTest, double[][] stockValues )
    {
        double[] estimations = new double[numberOfDaysToTest];
        double[] returns = VarUtils.computeDailyReturns(FileHelper.getClosingPrices( allStockQuotes_.get( 0 ) ));
        int numberOfReturnsToUse = returns.length - 1 - numberOfDaysToTest;

        for( int day = 0 ; day < numberOfDaysToTest ; day++ )
        {
            double[] returnsToUse = Arrays.copyOf( returns, numberOfReturnsToUse );
            double volatility = VarUtils.computeVolatility_GARCH( returnsToUse );
            MonteCarloResults monteCarloResults = computeForOneStock( portfolioValues.get( 0 ), volatility, stockValues );
            estimations[day] = monteCarloResults.finalVaR;
            numberOfReturnsToUse++;
        }
        return estimations;
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
