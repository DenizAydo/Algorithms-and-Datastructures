package p1.comparator;

import p1.card.Card;
import p1.card.CardColor;

import java.util.Comparator;

/**
 * Compares two {@link Card}s.
 * <p>
 * The cards are first compared by their value and then by their {@link CardColor}.
 *
 * @see Card
 */
public class CardComparator implements Comparator<Card> {

    /**
     * Compares two {@link Card}s.
     * <p>
     * The cards are first compared by their value and then by their {@link CardColor}.
     * <p>
     * The value of the cards compared by the natural order of the {@link Integer} class.
     * <p>
     * The color of the cards compared using the following order: {@link CardColor#CLUBS} > {@link CardColor#SPADES} >.{@link CardColor#HEARTS} > {@link CardColor#DIAMONDS}.
     *
     * @param o1 the first {@link Card} to compare.
     * @param o2 the second {@link Card} to compare.
     * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
     * @throws NullPointerException if either of the {@link Card}s is null.
     *
     * @see Card
     * @see CardColor
     * @see Comparator#compare(Object, Object)
     */
    @Override
    public int compare(Card o1, Card o2) { // 2/3 points

        if (o1 == null || o2 == null) {
            throw new NullPointerException();
        }

        int valueComparison = Integer.compare(o1.cardValue(), o2.cardValue());

        if (valueComparison != 0) {
            return valueComparison;
        }

        if(o1.cardColor() == o2.cardColor()) {
            return 0;
        }
        else if (o1.cardColor() == CardColor.CLUBS) {
            return -1;
        }
        else if (o2.cardColor() == CardColor.CLUBS) {
            return 1;
        }
        else if (o1.cardColor() == CardColor.SPADES) {
            return -1;
        }
        else if (o2.cardColor() == CardColor.SPADES) {
            return 1;
        }
        else if (o1.cardColor() == CardColor.HEARTS) {
            return -1;
        } else {
            return 1;
        }
    }
}
