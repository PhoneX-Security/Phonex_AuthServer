SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE Category;
SET FOREIGN_KEY_CHECKS=1;

INSERT INTO Category (id, name, owner, privateCategory, parent_id, description) VALUES
(2, 'MaiYY',                        1, 0, NULL,  NULL),
(1, 'Main category from bundle',    3, 0, 2,  NULL),
(4, 'MaUU ma in',                   1, 0, 2,  NULL),
(5, 'MaQQ Main',                    1, 0, 2,  NULL),
(6, 'MaTT mmiann',                  1, 0, 2,  NULL),
(7, 'MaHH Maim',                    1, 0, 2,  NULL),
(8, 'MaHH Máin',                    1, 0, 4,  NULL),
(9, 'MaHH M.a.i.n',                 1, 0, 8,  NULL),
(10, 'MaHH M"ai"n',                 1, 0, 9,  NULL),
(11, 'MaGG Mains',                  1, 0, 9,  NULL),

(100, 'ROOT_31',                    31, 0, NULL,  NULL),
(102, 'ROOT',                       11, 0, NULL,  NULL),
(104, 'ROOT|A',                     11, 0, 102,  NULL),
(105, 'ROOT|B',                     11, 0, 102,  NULL),
(106, 'ROOT|C',                     11, 0, 102,  NULL),
(107, 'ROOT|D',                     11, 0, 102,  NULL),
(108, 'ROOT|A|A',                   11, 0, 104,  NULL),
(109, 'ROOT|A|A|A',                 11, 0, 108,  NULL),
(110, 'ROOT|A|A|A|A',               11, 0, 109,  NULL),
(111, 'ROOT|A|A|A|B',               11, 0, 109,  NULL);
