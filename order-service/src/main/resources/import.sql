INSERT INTO customer_order (id, user_id, total_amount) VALUES (101, 1, 99.99);
INSERT INTO customer_order (id, user_id, total_amount) VALUES (102, 2, 45.50);
INSERT INTO customer_order (id, user_id, total_amount) VALUES (103, 1, 12.75);

INSERT INTO order_item (id, order_id, book_id, quantity, price) VALUES (1001, 101, 55, 1, 99.99);
INSERT INTO order_item (id, order_id, book_id, quantity, price) VALUES (1002, 102, 68, 1, 45.50);
INSERT INTO order_item (id, order_id, book_id, quantity, price) VALUES (1003, 103, 70, 1, 12.75);
