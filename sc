import pickle
import time
from datetime import datetime


from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options

from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from selenium.webdriver.common.action_chains import ActionChains
import hashlib
import extraer_num as ex

h = 0
class HeavenBanBot:
    
    def __init__(self, bot_credentials, chrome_driver_path="/usr/local/bin/chromedriver"):
        
        driver_path = chrome_driver_path
        chrome_options = Options()
        chrome_options.add_argument("--headless")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        service = Service(executable_path=driver_path)

        self.driver = webdriver.Chrome(service=service, options=chrome_options)
        self.logged_in = False
        self.bot_credentials = bot_credentials
        self.login()

    def login(self):
        self.driver.get("https://twitter.com/i/flow/login")
        print("Logging in as", self.bot_credentials["username"])
        wait = WebDriverWait(self.driver, 100)

        try:
            print("Trying log in w/ saved cookies")
            cookies = pickle.load(open("cookies.pkl", 'rb'))
            for cookie in cookies:
                # print(cookie)
                self.driver.add_cookie(cookie)
            #self.driver.get("https://twitter.com")
            print("Loaded Cookies!")
            #time.sleep(5)
            #optener cookies
            pickle.dump(self.driver.get_cookies(), open("cookies.pkl", "wb"))

            return True
        except Exception as e:
            print("Failed Getting Cookies:", str(e))
            pass

        # print(self.driver.text)
        # print(self.driver.page_source)
        # print(self.driver.find_element(By.XPATH, "/html/body").text)
        input = wait.until(EC.visibility_of_element_located(
            (By.CSS_SELECTOR, "input[autocomplete=\"username\"]")))
        # input = self.driver.find_element(By.CSS_SELECTOR, 'input[autocomplete=\"username\"]')
        input.click()
        input.send_keys(self.bot_credentials["username"])

        # find the next button
        # all_buttons = self.driver.find_elements(By.CSS_SELECTOR, '[role="button"]')
        # element = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, 'div[role="button"]')))
        # find the next button
        element = wait.until(EC.element_to_be_clickable(
            (By.CSS_SELECTOR, '[role="button"]')))
        element.click()

        password = wait.until(EC.visibility_of_element_located(
            (By.CSS_SELECTOR, "input[autocomplete=\"current-password\"]")))
        # password = self.driver.find_element(By.CSS_SELECTOR, 'input[autocomplete=\"current-password\"]')
        password.click()
        password.send_keys(self.bot_credentials["password"])

        login = self.driver.find_element(
            By.CSS_SELECTOR, "[data-testid=\"LoginForm_Login_Button\"]")
        login.click()
        print("Successfully logged in")
        #time.sleep(10)

        self.logged_in = True

    def scrape_recent_user_tweets(self,user , n=10):
        print(f"Scraping {n} tweets from {user}")
        wait = WebDriverWait(self.driver, 100)

        self.driver.get(f"https://twitter.com/{user}")
        #time.sleep(10)
        tweet_selector = "article[data-testid=\"tweet\"]"
        wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR, tweet_selector)))

        found_tweets = []  # array of strings
        

        global h
        # Función para mapear los datos de un tweet a un diccionario
        def map_tweet(tweet_element):
            
            # --------------
            tweet_data = {}
            try:
                tweet_data["Texto_tweet"] = tweet_element.find_element(
                    By.CSS_SELECTOR, "[data-testid=\"tweetText\"]").text
            except Exception as e:
                print("Error al extraer el texto del tweet:", str(e))
                tweet_data["Texto_tweet"] = None
            try:
                tweet_data["Autor_tweet"] = tweet_element.find_element(By.XPATH, './/span[contains(@class, "css-901oao css-16my406")]').text
            except Exception as e:
                print("Error al extraer el autor del tweet:", str(e))
                tweet_data["Autor_tweet"] = None
            try:
                newfecha = tweet_element.find_element(By.XPATH, './/time').get_attribute('datetime')
                # Convertir la cadena a un objeto datetime
                fecha_hora = datetime.strptime(
                    newfecha, "%Y-%m-%dT%H:%M:%S.%fZ")
                # Obtener solo la fecha en formato YYYY-MM-DD
                fecha_str = fecha_hora.strftime("%Y-%m-%d")
                tweet_data["Fecha"] = fecha_str
            except Exception as e:
                print("Error al extraer la fecha y hora del tweet:", str(e))
                tweet_data["Fecha"] = None
            try:
                all_items = tweet_element.find_element("css selector", '[role="group"]')
                respuestas, reposts, me_gusta, elementos_guardados, reproducciones = ex.extract_row_information(all_items.get_attribute("aria-label"))
                
                tweet_data["Numero_Respuestas"] = respuestas
                tweet_data["Numero_Retweets"] = reposts
                tweet_data["Numero_gusta"] = me_gusta
                tweet_data["Numero_guardados"] = elementos_guardados
                tweet_data["Numero_visualizacion"] = reproducciones
                
            except Exception as e:
                print("Error al extraer los datos de cada elemento del tweet:", str(e))
                tweet_data["Numero_Respuestas"] = 0
                tweet_data["Numero_Retweets"] = 0
                tweet_data["Numero_gusta"] = 0
                tweet_data["Numero_guardados"] = 0
                tweet_data["Numero_visualizacion"] = 0
            
            # Generar un ID único para el tweet basado en el texto y la fecha/hora
            tweet_text = tweet_data.get("Texto_tweet", "")
            tweet_datetime = tweet_data.get("Fecha_Hora", "")
            tweet_id = f"Tweet {h}"
            tweet_data["ID_tweet"] = tweet_id
            
            
            return tweet_data

        # --------------
        def filter_tweet(tweet_element):
            # must have tweet text greater than 3 words
            try:
                tweet_text_element = tweet_element.find_element(
                    By.CSS_SELECTOR, "[data-testid=\"tweetText\"]").text
                word_count_condition = len(tweet_text_element.split(" ")) > 3
            except:
                word_count_condition = False

            return word_count_condition

        while len(found_tweets) < n:
            tweets = self.driver.find_elements(By.CSS_SELECTOR, tweet_selector)
            tweets = list(filter(filter_tweet, tweets))  # Filtrar tweets
            # tweets = map(map_tweet, filter(filter_tweet, tweets))
            # Mapear tweets a diccionarios
            tweets_data = list(map(map_tweet, tweets))
            h+=1

            # Agregar los diccionarios de tweets a la lista
            found_tweets.extend(tweets_data)
            
            self.driver.execute_script("window.scrollBy(0, 3500)")
            #time.sleep(1)

        return found_tweets

        








