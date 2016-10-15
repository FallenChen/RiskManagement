package com.rp.var.analytics.security.options;

import com.rp.var.model.Option;
import com.rp.var.analytics.portfolio.VarUtils;
import org.apache.log4j.Logger;

/**
 * Class implementing the Binomial Tree option pricing algorithm.
 * Can be initialised with an {@link Option} or separate values.
 */
public class BinomialTree implements OptionPricer {
    private static final Logger logger_ = Logger.getLogger(BinomialTree.class);

    private double S, X, T, volatility, interest;
    private Option.OptionType optionType_;
    private Option.OptionStyle optionStyle_;
    /** The time step. One day is desirable. */
    // private int n = 1;
    private int      numberOfSteps;
    private double   dt;
    private double   p;
    private double   optionPrice;

    /**
     * Constructor to initialise a binomial tree using parameters of an option
     * @param s initial stock price
     * @param x strike price
     * @param t in years, time to maturity
     * @param volatility of the underlying asset
     * @param interest rate of option
     * @param type of option
     */
    public BinomialTree(double s, double x, double t, double volatility, double interest, Option.OptionType optionType, Option.OptionStyle optionStyle )
    {
        S = s;
        X = x;
        T = t;
        this.volatility = volatility;
        this.interest = interest;
        this.optionType_ = optionType;
        this.optionStyle_ = optionStyle;
        buildStockPriceTree();
    }

    /**
     * Constructor to initialise a binomial tree using an option.
     * Infers parameters from the option.
     * @param option the option to price
     */
    public BinomialTree( Option option, double rate, double volatility )
    {
        this.S = option.getInitialStockPrice();
        this.X = option.getStrike();
        this.T = option.getTimeToMaturity(); // in days
        this.optionType_ = option.getOptionType();
        this.optionStyle_ = option.getOptionStyle();

        this.volatility = volatility;
        this.interest = rate;
        buildStockPriceTree();
    }
    
    /**
     * Builds the binomial tree with stock prices going up and down.
     */
    private void buildStockPriceTree()
    {
        double u, d;

        // p is probability of up move, 1-p probability of down move

        // convert T to days
        // double days = T * DAYS_IN_YEAR;
        // int monthlySteps = (int) days / DAYS_IN_MONTH;

        // dailySteps = days / day in year
        int numberOfDays = (int) Math.floor( T * 252 );

        // dt = T / steps
        dt = T / numberOfDays;

        // http://en.wikipedia.org/wiki/Binomial_options_pricing_model

        u = Math.exp( ( volatility * Math.sqrt( dt ) ) );
        d = 1 / u;

        // u >= 1, 0 < d <= 1
        if( ( u < 1 ) || ( d < 0 ) || ( d > 1 ) )
        {
            logger_.error( "Error calculating u and d." );
            throw new IllegalStateException("Error calculating u and d.");
        }

        // p = (e^(rdt) - d) / (u-d) (between 0 and 1) if dt < (variance)/(r-q)^2
        double ert = Math.exp( interest * dt );
        double ertminusd = ert - d;
        double uminusd = u - d;
        p = ertminusd / uminusd;

        if( p < 0 || p > 1 )
        {
            throw new IllegalStateException("Error: p = " + p );
        }

        // numberOfSteps = (int) ( T * 12 ) + 1;
        numberOfSteps = numberOfDays + 1;

        double[][] tree = new double[numberOfSteps][numberOfSteps];
        tree[0][0] = S;

        for( int row = 0 ; row < numberOfSteps ; row++ )
        {
            int startDay = row;
            for( int day = startDay ; day < numberOfSteps ; day++ )
            {
                if( row == 0 && day == 0 )
                {
                    continue;
                }
                else if( row == day )
                {
                    tree[row][day] = tree[row - 1][day - 1] * d;
                    continue;
                }
                // up
                tree[row][day] = tree[row][day - 1] * u;
                // down
            }
        }

        //printTree( tree );

        calculateOptionPrices( tree );

    }
    
    /**
     * Calculates the option prices using a tree of stock prices.
     * @param tree model of stock prices going up and down in a binomial tree
     */
    private double calculateOptionPrices( double[][] tree )
    {
        double[][] values = new double[tree.length][tree.length];

        // getOptionPrice values at final nodes
        // at each final node (expiration of Option) option value is intrinsic/exercise value

        // for Call - Max [(Sn - X), 0] Sn = price of stock on that day
        // for Put - Max [(X - Sn), 0]
        for( int row = 0 ; row < numberOfSteps ; row++ ) {
            int column = numberOfSteps - 1;

            switch (optionStyle_)
            {
                case American:
                    switch (optionType_) {
                        case Call:
                            values[row][column] = Math.max((tree[row][column] - X), 0);
                            break;
                        case Put:
                            values[row][column] = Math.max((X - tree[row][column]), 0);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown option type [" + optionType_ + "]");
                    }
                case European:
                    switch (optionType_) {
                        case Call:
                            values[row][column] = Math.max((tree[row][column] - X), 0);
                            break;
                        case Put:
                            values[row][column] = Math.max((X - tree[row][column]), 0);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown option type [" + optionType_ + "]");
                    }
            }
        }
        if (logger_.isDebugEnabled())
            printTree( values );

        double eMinusRT = Math.exp( -1.0 * interest * dt );

        for( int column = numberOfSteps - 2 ; column >= 0 ; column-- )
        {
            for( int row = 0 ; row <= column ; row++ )
            {
                double binomialValue = 0.0;
                double intrinsicValue = 0.0;
                switch (optionStyle_)
                {
                    case American:
                        switch (optionType_)
                        {
                            case Call:
                                binomialValue = eMinusRT
                                        * ((p * values[row][column + 1]) + (1 - p)
                                        * values[row + 1][column + 1]);
                                // intrinsic value for call is Max(Sn - X, 0)
                                intrinsicValue = Math.max(tree[row][column] - X, 0);
                                values[row][column] = Math.max(binomialValue, intrinsicValue);
                                break;
                            case Put:
                                // early exercise possible, value = Max [Binomial Value, Exercise Value]
                                binomialValue = eMinusRT
                                        * ((p * values[row][column + 1]) + (1 - p)
                                        * values[row + 1][column + 1]);
                                intrinsicValue = Math.max(X - tree[row][column], 0);
                                // intrinsic value for put is Max(X-Sn, 0)
                                values[row][column] = Math.max(binomialValue, intrinsicValue);
                                break;
                        }
                    case European:
                        switch (optionType_)
                        {
                            case Call:
                                // no option of early exercise, so binomial value applies
                                binomialValue = eMinusRT
                                        * ( ( p * values[row][column + 1] ) + ( 1 - p )
                                        * values[row + 1][column + 1] );
                                values[row][column] = binomialValue;
                                break;
                            case Put:
                                binomialValue = eMinusRT
                                        * ( ( p * values[row][column + 1] ) + ( 1 - p )
                                        * values[row + 1][column + 1] );
                                values[row][column] = binomialValue;
                                break;
                        }
                }
            }
        }

        if (logger_.isDebugEnabled())
            printTree( values );
        optionPrice = values[0][0];

        return optionPrice;
    }
    /**
     * Prints the tree as a 2D table for visualisation of computation.
     * @param tree
     */
    private void printTree( double[][] tree )
    {
        for( int row = 0 ; row < tree[0].length ; row++ )
        {
            StringBuilder stringBuilder = new StringBuilder();
            for( int column = 0 ; column < tree.length ; column++ )
            {
                stringBuilder.append( VarUtils.roundTwoDP( tree[row][column] ) + "\t" );
            }
            logger_.debug(stringBuilder.toString());
        }
    }

    // TODO control variate technique
    /*
     * 1. value American option use tree to get fA
     * 2. value corresponding European option using same tree to get fE
     * 3. value European option using Black-Scholes to get fBS
     * 4. take fA + fBS - fE as estimate of price of American option
     * 5. (fBS - fE) can be thought of as the error of the tree method,
     * assume error for American option is the same.
     */
    
    /**
     * @return the final option price computed using the binomial tree method.
     */
    @Override
    public double getOptionPrice()
    {
        return optionPrice;
    }

}
