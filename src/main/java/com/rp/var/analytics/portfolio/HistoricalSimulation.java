/**
 * 
 */
package com.rp.var.analytics.portfolio;

import com.rp.var.analytics.security.options.BinomialTree;
import com.rp.var.analytics.security.options.BlackScholes;
import com.rp.var.analytics.security.options.OptionPricer;
import com.rp.var.model.Option;
import com.rp.var.model.Portfolio;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of the Historical Simulation VaR model.
 */
public class HistoricalSimulation
{
    private static final Logger logger_ = Logger.getLogger(HistoricalSimulation.class);
    /** List of investments for a portfolio. */
    private List<Double> portfolioValues;

    /** The confidence level to getOptionPrice VaR for. */
    private int               confidence;
    /** The portfolio being used for VaR computation. */
    private Portfolio portfolio;
    private int varHorizon;

    /**
     * Constructor to initialise a Historical Simulation model using a portfolio and a confidence level.
     * @param portfolio the portfolio to getOptionPrice VaR for.
     * @param confidence the confidence level at which to getOptionPrice VaR.
     */
    public HistoricalSimulation( Portfolio portfolio, int confidence )
    {
        this(portfolio,confidence,1);
    }

    public HistoricalSimulation( Portfolio portfolio, int confidence, int varHorizon )
    {
        this.portfolio = portfolio;
        this.portfolioValues = portfolio.getInvestments();
        this.confidence = confidence;
        this.varHorizon= varHorizon;
    }


    /**
     * Computes value at risk.
     */
    public double computeValueAtRisk()
    {
        if( portfolioValues.size() == 1 )
        {
            double var =computeValueAtRisk_OneStock();
            logger_.debug( "Historical Simulation VaR (" + portfolioValues.size()
                                + " stocks): " + var );
            return var;
        }
        else
        {

            List<double[]> portfolioReturns = portfolio.getReturns();

            /*
             * System.out.println( "Historical Simulation VaR (" + portfolioValues.size()
             * + " stocks): "
             * + Math.round( computeForMultipleStocks( portfolioReturns ) ) );
             */
            return computeForMultipleStocks( portfolioReturns );
        }

    }
    
    /**
     * Computes value at risk.
     * @return array containing final and max VaRs.
     */
    public double[] computeValueAtRiskForPortfolio()
    {
        return computeForPortfolio();
    }

    /**
     * Initial implementation of the historical simulation method of computing
     * VaR. Strategy used:
     * <ol>
     * <li>get sorted list of periodic returns</li>
     * <li>getOptionPrice desired percentile from requested confidence level</li>
     * <li>lookup this percentile in the sorted list of returns and get the return at this location</li>
     * <li>multiply the portfolio value by the selected return as a measure of VaR</li>
     * </ol>
     * 
     * @return
     */
    private double computeValueAtRisk_OneStock()
    {
        double[] returns = portfolio.getReturns( 0 );
        return getVaROneStock( returns );
    }
    
    /**
     * Get the VaR estimate from a percentile of the given returns.
     * @param returns historical returns of the asset
     * @return VaR at the percentile relevant to the confidence level from the returns.
     */
    private double getVaROneStock( double[] returns )
    {
        Arrays.sort( returns );
        double percentile = VarUtils.getPercentile( returns, confidence );
        double var = portfolioValues.get( 0 )
                     - ( portfolioValues.get( 0 ) * Math.exp( percentile ) );
        return var;
    }

    /**
     * Computes VaR for multiple stocks.
     * @return final VaR
     */
    public double computeForMultipleStocks( List<double[]> portfolioReturns )
    {
        double var = 0.0;

        int numberOfStocks = portfolioReturns.size();

        // should be the smallest of the lengths of the array of returns.
        int[] lengthsOfReturns = new int[portfolioValues.size()];
        for( double[] arr : portfolioReturns )
        {
            lengthsOfReturns[portfolioReturns.indexOf( arr )] = arr.length;
        }
        Arrays.sort( lengthsOfReturns );
        int numberOfReturns = lengthsOfReturns[0];

        logger_.debug( "\t Days of data from returns: " + numberOfReturns );

        double[] possiblePortfolioValues = new double[numberOfReturns];

        // calculate overall value for each previous return
        for( int i = 0 ; i < numberOfReturns ; i++ )
        {
            double possibleChange = 0.0;
            for( int n = 0 ; n < portfolioValues.size() ; n++ )
            {
                possibleChange += portfolioValues.get( n )
                                  * Math.exp( portfolioReturns.get( n )[i] );
            }
            possiblePortfolioValues[i] = possibleChange;
        }

        Arrays.sort( possiblePortfolioValues );

        double valueAtPercentile = VarUtils.getPercentile( possiblePortfolioValues, confidence );
        double portfolioValue = 0.0;
        for( double value : portfolioValues )
        {
            portfolioValue += value;
        }
        valueAtPercentile = portfolioValue - valueAtPercentile;
        var = Math.round( Math.abs( valueAtPercentile ) );

        double[]finalMaxVars = new double[2];
        finalMaxVars[0] = var;
        double maxVar = portfolioValue - possiblePortfolioValues[0];
        finalMaxVars[1] = maxVar;

        return var;
    }

    /** Computes VaR for portfolio. */
    private double[] computeForPortfolio()
    {
        double initialPortFolioValue = 0.0;
        double finalPortfolioValue = 0.0;

        List<Double> investments = portfolio.getInvestments();

        double initialOptionsValue = 0.0;

        List<Option> options = portfolio.getOptions();

        for( Option option : options )
        {
            initialOptionsValue += ( option.getInitialStockPrice() * option.getNumShares() );
        }

        initialPortFolioValue = VarUtils.sumOf( investments ) + initialOptionsValue;

        /***************** STOCKS *********************/
        // use previous functionality to getOptionPrice final prices of the portfolio and options
        List<double[]> portfolioReturns = VarUtils.getReturnsFromFiles( portfolio.getStockPriceDataFiles() );
        int numberOfStocks = portfolioReturns.size();

        // should be the smallest of the lengths of the array of returns.
        int[] lengthsOfReturns = new int[numberOfStocks];
        for( double[] arr : portfolioReturns )
        {
            lengthsOfReturns[portfolioReturns.indexOf( arr )] = arr.length;
        }
        Arrays.sort( lengthsOfReturns );
        int numberOfReturns = lengthsOfReturns[0];

        double[] possiblePortfolioValues = new double[numberOfReturns];

        // calculate overall value for each previous return
        for( int i = 0 ; i < numberOfReturns ; i++ )
        {
            double possibleChange = 0.0;
            for( int n = 0 ; n < numberOfStocks ; n++ )
            {
                possibleChange += portfolioValues.get( n )
                                  * Math.exp( portfolioReturns.get( n )[i] );
            }
            possiblePortfolioValues[i] = possibleChange;
        }

        Arrays.sort( possiblePortfolioValues );

        double stocksValueAtPercentile = VarUtils.getPercentile( possiblePortfolioValues,
                                                                 confidence );

        /***************** OPTIONS *********************/
        double optionsFinalValue = 0.0, optionsMinValue = 0.0;
        for( Option option : options )
        {
            double[] priceOption = priceOption( option );
            optionsFinalValue += priceOption[0];
            optionsMinValue += priceOption[1];
        }

        finalPortfolioValue = stocksValueAtPercentile + optionsFinalValue;

        double portfolioFinalVaR = initialPortFolioValue - finalPortfolioValue;
        double portfolioMinValue = possiblePortfolioValues[0] + optionsMinValue;
        double portfolioMaxVaR = initialPortFolioValue - portfolioMinValue;

        double[]finalMaxVars = new double[2];
        finalMaxVars[0] = Math.round( portfolioFinalVaR );
        finalMaxVars[1] = Math.round( portfolioMaxVaR );

        return finalMaxVars;

    }
    
    /**
     * Prices an option using a pre-defined option pricing type.
     * @param option the option to value
     * @return final and minimum option prices during simulation for VaR computation.
     */
    private double[] priceOption( Option option )
    {
        double[] finalMinOptionsValue = new double[2];
        // get returns from file
        double[] returns = VarUtils.getReturnsFromFile( option.getPriceData() );
        int numberOfReturns = returns.length;
        List<Double> possibleOptionValues = new ArrayList<Double>();
        // getOptionPrice possible value change for each return in data
        double historicalValue = 0.0;
        double stockPrice = 0.0;
        int initialTimeToMaturity = option.getTimeToMaturity();
        for( int i = 0 ; i < numberOfReturns ; i++ )
        {
            // TODO need to check if this is the right price used
            int currentTimeToMaturity = initialTimeToMaturity - i;
            stockPrice = Math.exp( returns[i] ) * option.getInitialStockPrice();
            if( currentTimeToMaturity >= 0 )
            {
                switch( option.getOptionStyle() )
                {
                    case European:
                        switch (option.getOptionType())
                        {
                            case Call:
                            case Put:
                                BlackScholes bs = new BlackScholes(option.getOptionType(), stockPrice, option.getStrike(),
                                        currentTimeToMaturity,
                                        option.getInterest(),
                                        option.getDailyVolatility());
                                historicalValue = bs.getOptionPrice();
                                break;
                            default:
                                throw new IllegalArgumentException("Unable to handle option type ["+option.getOptionType()+"]");
                        }
                    case American:
                        // update the initial price and maturity to today's value
                        option.setInitialStockPrice( stockPrice );
                        option.setTimeToMaturity( currentTimeToMaturity );
                        OptionPricer bt = new BinomialTree( option, option.getInterest(), option.getDailyVolatility() );
                        historicalValue = bt.getOptionPrice();
                        break;
                    default:
                        option.setInitialStockPrice( stockPrice );
                        option.setTimeToMaturity( currentTimeToMaturity );
                        MonteCarloSimulation mc = new MonteCarloSimulation();
                        historicalValue = mc.priceOptionUsingMonteCarlo( option )[0];
                        break;
                }

                possibleOptionValues.add( historicalValue );
            }
            else
            {
                break;
            }
        }
        double[] values = new double[possibleOptionValues.size()];
        for( int i = 0 ; i < possibleOptionValues.size() ; i++ )
        {
            values[i] = possibleOptionValues.get( i );
        }
        // sort possible values
        Arrays.sort( values );

        // select value from percentile
        double valueAtPercentile = VarUtils.getPercentile( values, confidence );
        double minValue = values[0];
        finalMinOptionsValue = new double[] { valueAtPercentile, minValue };
        return finalMinOptionsValue;
    }

    /**
     * Estimates VaR for backtesting purposes.
     * @param numberOfDaysToTest the number of days to estimate VaR for
     * @return estimations of VaR over the next number of days specified
     */
    public double[] estimateVaRForBackTestingOneStock( int numberOfDaysToTest )
    {
        double[] returns = portfolio.getReturns( 0 );
        int numberOfReturnsToUse = returns.length - 1 - numberOfDaysToTest;
        double[] estimations = new double[numberOfDaysToTest];

        for( int day = 0 ; day < numberOfDaysToTest ; day++ )
        {
            double[] returnsToUse = Arrays.copyOf( returns, numberOfReturnsToUse );
            double[] returnsOverVarHorizon = VarUtils.getReturnsOverVarHorizon( returnsToUse, varHorizon );
            estimations[day] = getVaROneStock( returnsOverVarHorizon );
            numberOfReturnsToUse++;
        }

        return estimations;
    }

}